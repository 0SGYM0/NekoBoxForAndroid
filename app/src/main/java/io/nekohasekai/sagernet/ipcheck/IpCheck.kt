package io.nekohasekai.sagernet.ipcheck

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject

/**
 * 出口 IP 检测（复刻 ip.net.coffee 的纯净度/场景评估逻辑）
 *
 * 通过 sing-box 的本地 mixed 端口（socks5）发请求，请求会按域名走用户配置的
 * 分流规则，从而分别拿到「默认出口 / ChatGPT 出口 / Claude 出口」三个不同 IP。
 */
data class SceneScore(
    val score: Double,
    val verdict: String,
    val blocked: Boolean,
    val note: String,
)

data class IpReport(
    val ip: String,
    val country: String,
    val region: String,
    val city: String,
    val countryCode: String,
    val trustScore: Int,
    val scoreLabel: String,
    val isResidential: Boolean,
    val isDatacenter: Boolean,
    val isNative: Boolean,
    val isBroadcast: Boolean,
    val companyType: String,
    val companyName: String,
    val asn: String,
    val asOrganization: String,
    val isp: String,
    val humanPercent: Int?,       // 人类流量占比(0-100)，无数据为 null
    val humanLabel: String,       // 人类偏多 / 人机混合 / 机器偏多 / IPv6 不评估
    val flags: List<String>,      // 命中的风险标签
    val isIpv6: Boolean,
    val scenes: Map<String, SceneScore>, // tiktok / social / ai
    val aiRestricted: Boolean,
    val aiRestrictedName: String?,
)

object IpChecker {

    private const val TRACE_DEFAULT = "https://1.1.1.1/cdn-cgi/trace"
    private const val TRACE_CHATGPT = "https://chatgpt.com/cdn-cgi/trace"
    private const val TRACE_CLAUDE = "https://claude.ai/cdn-cgi/trace"
    private const val LOOKUP_URL = "https://ip.net.coffee/api/ipv2/lookup/%s"
    private const val RADAR_URL = "https://ip.net.coffee/api/ipv2/radar/%s"
    private const val USER_AGENT = "curl/8.14.1"

    // OpenAI 官方支持列表之外的受限地区（ISO-2）
    private val OPENAI_RESTRICTED_CC = mapOf(
        "CN" to "中国大陆", "HK" to "香港", "MO" to "澳门",
        "RU" to "俄罗斯", "KP" to "朝鲜", "IR" to "伊朗", "SY" to "叙利亚",
        "CU" to "古巴", "BY" to "白俄罗斯", "VE" to "委内瑞拉",
    )

    private data class Region(val block: List<String>, val partial: List<String>)

    private val REGION = mapOf(
        "tiktok" to Region(
            block = listOf("CN", "HK", "IN", "IR", "AF", "KP", "JO", "SO", "SN", "KG", "UZ"),
            partial = emptyList(),
        ),
        "social" to Region(block = listOf("CN", "IR", "KP", "TM"), partial = listOf("RU", "MM")),
        "ai" to Region(
            block = listOf("CN", "RU", "BY", "IR", "KP", "CU", "SY", "AF"),
            partial = listOf("HK", "MO", "VE", "MM"),
        ),
    )

    private fun roundHalfUp(x: Double): Double = Math.floor(x + 0.5)

    private fun ccZh(cc: String, fallback: String): String {
        val known = mapOf(
            "TW" to "台湾省", "HK" to "香港", "MO" to "澳门",
            "CN" to "中国大陆", "US" to "美国", "JP" to "日本", "SG" to "新加坡",
            "KR" to "韩国", "RU" to "俄罗斯", "IN" to "印度",
        )
        return known[cc.uppercase()] ?: fallback
    }

    // ============ 网络请求（走 sing-box socks5 代理） ============

    private fun httpGet(url: String): String {
        val resp = Libcore.newHttpClient().apply {
            requireSocks5(DataStore.mixedPort)
            setTimeoutMillis(8000)
            setMaxResponseBytes(262144)
        }.newRequest().apply {
            setURL(url)
            setUserAgent(USER_AGENT)
        }.execute()
        return Util.getStringBox(resp.contentString)
    }

    private fun getExitIp(traceUrl: String): String {
        val body = httpGet(traceUrl)
        val line = body.lineSequence().firstOrNull { it.startsWith("ip=") }
            ?: error("trace 无 ip 字段")
        return line.removePrefix("ip=").trim().ifEmpty { error("trace ip 为空") }
    }

    private fun lookupJson(ip: String): JSONObject {
        val encoded = java.net.URLEncoder.encode(ip, "UTF-8")
        return JSONObject(httpGet(String.format(LOOKUP_URL, encoded)))
    }

    private fun radarHuman(asn: String): Int? {
        return try {
            val j = JSONObject(httpGet(String.format(RADAR_URL, asn)))
            if (j.optBoolean("ok") && j.has("human")) {
                val h = j.optDouble("human", -1.0)
                if (h in 0.0..100.0) h.toInt() else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ============ 场景评估（复刻 ip.net.coffee sceneScores） ============

    private fun humanEstimate(d: JSONObject): Double {
        val companyType = d.optString("company_type", "").lowercase()
        val asnKind = d.optString("asn_kind", "").lowercase()
        return when {
            d.optBoolean("is_public_service") -> 2.0
            d.optBoolean("is_crawler") -> 6.0
            d.optBoolean("is_mobile") || asnKind == "mobile" -> 93.0
            d.optBoolean("is_datacenter") || companyType == "hosting" || asnKind == "hosting" || asnKind == "cdn" -> 18.0
            companyType == "business" -> 65.0
            else -> 88.0
        }
    }

    private fun humanAdjust(h: Double, d: JSONObject): Double {
        var v = h
        if (d.optBoolean("is_tor")) v *= 0.4
        else if (d.optBoolean("is_proxy") || d.optBoolean("is_vpn")) v *= 0.6
        if (d.optBoolean("is_crawler")) v = Math.min(v, v * 0.3 + 2)
        if (d.optBoolean("is_abuser")) v -= 5
        return Math.max(1.0, Math.min(99.0, v))
    }

    private fun sceneScores(d: JSONObject): Map<String, SceneScore> {
        val trust = d.optDouble("trust_score", -1.0)
        if (trust < 0) return emptyMap()

        val base = roundHalfUp(trust / 10)
        val cc = d.optString("countryCode", "").uppercase()
        val reg = d.optString("registered_country_code", "").uppercase()
        val companyType = d.optString("company_type", "").lowercase()
        val asnKind = d.optString("asn_kind", "").lowercase()

        val dc = d.optBoolean("is_datacenter") || companyType == "hosting" || asnKind == "hosting" || asnKind == "cdn"
        val native = cc.isNotEmpty() && reg.isNotEmpty() && cc == reg
        val broadcast = cc.isNotEmpty() && reg.isNotEmpty() && cc != reg
        val human = !dc && !d.optBoolean("is_crawler") && !d.optBoolean("is_public_service")
        val business = companyType == "business"
        val pv = d.optBoolean("is_proxy") || d.optBoolean("is_vpn") || d.optBoolean("is_tor")

        val intel = d.optJSONObject("intelligence")
        val abuserScoreRaw = intel?.optString("abuser_score_raw", "") ?: ""
        val abuserScore = d.optString("abuser_score", "") ?: ""
        val abRaw = if (abuserScoreRaw.isNotEmpty()) abuserScoreRaw else abuserScore
        val abPen = when {
            abRaw.isNotEmpty() -> {
                val v = abRaw.toDoubleOrNull() ?: 0.0
                when {
                    v > 0.05 -> 2.0
                    v > 0.025 -> 1.0
                    v > 0.01 -> 0.5
                    else -> 0.0
                }
            }
            else -> {
                val lv = (intel?.optString("abuser_level", "") ?: "").lowercase()
                when {
                    lv == "high" || lv == "very_high" || lv == "veryhigh" -> 2.0
                    lv == "elevated" -> 1.0
                    else -> 0.0
                }
            }
        }

        val repThreat = intel?.optString("rep_threat", "") ?: ""
        val httpbl = intel?.optString("httpbl_threat", "") ?: ""
        val hpRaw = if (repThreat.isNotEmpty()) repThreat else httpbl
        val hp = hpRaw.toDoubleOrNull() ?: 0.0
        val hpPen = if (hp > 0) if (hp <= 25) 1.0 else 2.0 else 0.0

        val threats = d.optBoolean("is_abuser")
        val crawler = d.optBoolean("is_crawler")
        val risky = threats || abPen > 0 || hpPen > 0 || crawler || pv

        fun calc(kind: String): SceneScore {
            var s = base
            if (native) s += 0.5
            if (human) s += 0.5
            if (business) s -= 0.5
            if (dc) s -= 2
            if (broadcast) s -= 1
            if (kind != "ai" && dc) s -= 1
            if (pv) s -= if (kind == "ai") 2.0 else 3.0
            if (threats) s -= if (kind == "ai") 0.5 else 1.0
            s -= abPen
            s -= hpPen
            if (crawler) s -= 0.5
            s = roundHalfUp(s)
            if (s < 0) s = 0.0
            if (s > 10) s = 10.0
            if (risky && s > 9) s = 9.0

            val r = REGION[kind]!!
            val blocked = r.block.contains(cc)
            val partial = r.partial.contains(cc)
            if (blocked) {
                return SceneScore(0.0, "地区不可用", true, "${ccZh(cc, cc)}：该地区不可用")
            }
            if (partial && s > 5) s = 5.0

            val verdict = when {
                partial -> "部分可用"
                s >= 10 -> "极佳"
                s >= 8 -> "推荐"
                s >= 5 -> if (kind == "ai") "可以尝试" else "可用"
                else -> "不推荐"
            }
            return SceneScore(s, verdict, false, if (partial) "${ccZh(cc, cc)}：该地区部分可用" else "")
        }

        return mapOf(
            "tiktok" to calc("tiktok"),
            "social" to calc("social"),
            "ai" to calc("ai"),
        )
    }

    // ============ 组装报告 ============

    private fun scoreLabel(trustScore: Int): String = when {
        trustScore >= 95 -> "极度纯净"
        trustScore >= 80 -> "纯净"
        trustScore >= 50 -> "良好"
        trustScore >= 25 -> "中性"
        else -> "可疑"
    }

    private fun buildReport(ip: String, d: JSONObject, humanFromRadar: Int?): IpReport {
        val trustScore = d.optInt("trust_score", -1)
        val cc = d.optString("countryCode", "").uppercase()
        val reg = d.optString("registered_country_code", "").uppercase()
        val isIpv6 = ip.contains(":")

        val flags = mutableListOf<String>()
        if (d.optBoolean("is_tor")) flags.add("Tor出口")
        if (d.optBoolean("is_proxy")) flags.add("代理")
        if (d.optBoolean("is_vpn")) flags.add("VPN")
        if (d.optBoolean("is_crawler")) flags.add("爬虫")
        if (d.optBoolean("is_abuser")) flags.add("滥用")
        if (d.optBoolean("is_mobile")) flags.add("移动蜂窝")
        if (d.optBoolean("is_public_service")) flags.add("公共DNS")

        val humanPercent: Int?
        val humanLabel: String
        if (isIpv6 && !d.optBoolean("is_public_service")) {
            humanPercent = null
            humanLabel = "IPv6 暂不评估"
        } else {
            val final = humanFromRadar ?: humanAdjust(humanEstimate(d), d).toInt()
            humanPercent = final
            humanLabel = when {
                final >= 55 -> "人类偏多"
                final >= 40 -> "人机混合"
                else -> "机器偏多"
            }
        }

        val scenes = if (isIpv6 && !d.optBoolean("is_public_service")) {
            emptyMap()
        } else {
            sceneScores(d)
        }

        val aiRestricted = cc in OPENAI_RESTRICTED_CC
        val aiRestrictedName = if (aiRestricted) OPENAI_RESTRICTED_CC[cc] else null

        return IpReport(
            ip = ip,
            country = d.optString("country", ""),
            region = d.optString("region", ""),
            city = d.optString("city", ""),
            countryCode = cc,
            trustScore = trustScore,
            scoreLabel = if (trustScore >= 0) scoreLabel(trustScore) else "",
            isResidential = d.optBoolean("isResidential"),
            isDatacenter = d.optBoolean("is_datacenter"),
            isNative = cc.isNotEmpty() && reg.isNotEmpty() && cc == reg,
            isBroadcast = cc.isNotEmpty() && reg.isNotEmpty() && cc != reg,
            companyType = d.optString("company_type", ""),
            companyName = d.optString("company_name", ""),
            asn = d.optString("asn", ""),
            asOrganization = d.optString("asOrganization", ""),
            isp = d.optString("isp", ""),
            humanPercent = humanPercent,
            humanLabel = humanLabel,
            flags = flags,
            isIpv6 = isIpv6,
            scenes = scenes,
            aiRestricted = aiRestricted,
            aiRestrictedName = aiRestrictedName,
        )
    }

    private suspend fun detectOne(traceUrl: String): IpReport {
        coroutineContext.ensureActive()
        val ip = getExitIp(traceUrl)
        coroutineContext.ensureActive()
        val d = lookupJson(ip)
        coroutineContext.ensureActive()
        val asn = if (d.isNull("asn")) "" else d.optString("asn", "").trim()
        // For ordinary IPv6 the renderer does not use radar data. Public services
        // remain evaluated, preserving the existing scoring behaviour.
        val needsRadar = !ip.contains(":") || d.optBoolean("is_public_service")
        val human = if (needsRadar && asn.matches(Regex("(?i)(AS)?[0-9]+"))) radarHuman(asn) else null
        coroutineContext.ensureActive()
        return buildReport(ip, d, human)
    }

    enum class Exit { DEFAULT, CHATGPT, CLAUDE }

    /** Callback runs in the caller's context, once per completed exit. */
    suspend fun detectEach(publish: (Exit, Result<IpReport>) -> Unit) {
        parallelChecks(Exit.values().toList(), { exit ->
            withContext(Dispatchers.IO) {
                detectOne(when (exit) {
                    Exit.DEFAULT -> TRACE_DEFAULT
                    Exit.CHATGPT -> TRACE_CHATGPT
                    Exit.CLAUDE -> TRACE_CLAUDE
                })
            }
        }, publish)
    }
}

/** 把 IpReport 渲染成多行文本（供面板 / BottomSheet 复用） */
object IpReportRenderer {

    fun render(context: android.content.Context, r: IpReport): String {
        val sb = StringBuilder()
        sb.append(r.ip).append('\n')

        val geo = listOf(r.country, r.region, r.city).filter { it.isNotBlank() }.joinToString(" · ")
        if (geo.isNotBlank()) {
            sb.append(context.getString(R.string.ipcheck_geo)).append(' ').append(geo).append('\n')
        }

        if (r.trustScore >= 0) {
            sb.append(context.getString(R.string.ipcheck_score))
                .append(' ').append(r.trustScore)
                .append(' ').append(r.scoreLabel).append('\n')
        }

        if (r.isNative || r.isBroadcast) {
            val nativeText = if (r.isNative) context.getString(R.string.ipcheck_native)
            else context.getString(R.string.ipcheck_broadcast)
            sb.append(context.getString(R.string.ipcheck_native_label)).append(' ').append(nativeText).append('\n')
        }
        if (r.isDatacenter) {
            sb.append(context.getString(R.string.ipcheck_datacenter)).append('\n')
        } else if (r.isResidential) {
            sb.append(context.getString(R.string.ipcheck_residential)).append('\n')
        }

        val org = listOf(r.companyName, r.isp).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        if (org.isNotBlank()) {
            sb.append(context.getString(R.string.ipcheck_provider)).append(' ').append(org).append('\n')
        }
        if (r.asn.isNotBlank()) {
            val asnText = if (r.asOrganization.isNotBlank()) "AS${r.asn} · ${r.asOrganization}" else "AS${r.asn}"
            sb.append(context.getString(R.string.ipcheck_asn)).append(' ').append(asnText).append('\n')
        }

        if (r.humanPercent != null) {
            val bot = 100 - r.humanPercent
            sb.append(context.getString(R.string.ipcheck_human))
                .append(" 人类 ").append(r.humanPercent).append("% / 机器 ").append(bot).append("%")
                .append("（").append(r.humanLabel).append("）").append('\n')
        } else if (r.humanLabel.isNotBlank()) {
            sb.append(context.getString(R.string.ipcheck_human)).append(' ').append(r.humanLabel).append('\n')
        }

        if (r.flags.isNotEmpty()) {
            sb.append(context.getString(R.string.ipcheck_flags)).append(' ').append(r.flags.joinToString("、")).append('\n')
        }

        if (r.scenes.isNotEmpty()) {
            val sceneParts = listOf("tiktok", "social", "ai").mapNotNull { key ->
                r.scenes[key]?.let { renderScene(context, key, it) }
            }
            if (sceneParts.isNotEmpty()) {
                sb.append(context.getString(R.string.ipcheck_scenes)).append(' ').append(sceneParts.joinToString(" · ")).append('\n')
            }
        } else if (r.isIpv6) {
            sb.append(context.getString(R.string.ipcheck_scenes)).append(' ').append(context.getString(R.string.ipcheck_ipv6_na)).append('\n')
        }

        sb.append(context.getString(R.string.ipcheck_ai_region))
            .append(' ').append(if (r.aiRestricted) "受限（${r.aiRestrictedName ?: r.countryCode}）" else "正常")
            .append('\n')

        return sb.toString().trimEnd()
    }

    private fun renderScene(context: android.content.Context, key: String, s: SceneScore): String {
        val name = when (key) {
            "tiktok" -> context.getString(R.string.ipcheck_scene_tiktok)
            "social" -> context.getString(R.string.ipcheck_scene_social)
            else -> context.getString(R.string.ipcheck_scene_ai)
        }
        return "$name ${s.verdict}"
    }
}
