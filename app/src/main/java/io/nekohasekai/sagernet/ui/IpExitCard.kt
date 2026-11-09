package io.nekohasekai.sagernet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ipcheck.IpReport
import java.util.Locale

/** Presentation only; each exit owns one always-expanded card. */
class IpExitCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    init { orientation = VERTICAL; addView(content) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun color(attr: Int): Int = TypedValue().let { value ->
        context.theme.resolveAttribute(attr, value, true)
        if (value.resourceId != 0) androidx.core.content.ContextCompat.getColor(context, value.resourceId) else value.data
    }
    private fun text(value: String, size: Float, bold: Boolean = false, secondary: Boolean = false) = TextView(context).apply {
        text = value; textSize = size
        setTextColor(color(if (secondary) android.R.attr.textColorSecondary else android.R.attr.textColorPrimary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun card(): LinearLayout {
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat(); cardElevation = dp(1).toFloat()
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
            strokeWidth = dp(1)
            strokeColor = color(com.google.android.material.R.attr.colorOutline).let { if (it == 0) color(android.R.attr.textColorSecondary) else it }
        }
        val box = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        card.addView(box)
        content.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return box
    }
    private fun header(box: LinearLayout, score: String? = null) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP }
        line.addView(text(tag?.toString() ?: context.getString(R.string.ipcheck_title), 16f, true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (score != null) line.addView(text(score, 14f, true).apply { gravity = Gravity.END; setPadding(dp(8), 0, 0, 0) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        box.addView(line)
    }
    private fun row(box: LinearLayout, label: String, value: String) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP; setPadding(0, dp(2), 0, dp(2)) }
        line.addView(text(label, 13f, secondary = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        line.addView(text(value, 13f).apply { gravity = Gravity.END }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f))
        box.addView(line)
    }
    fun clear() { content.removeAllViews() }
    fun showState(message: String) {
        clear()
        val box = card()
        header(box)
        box.addView(text(message, 13f, secondary = true).apply { setPadding(0, dp(7), 0, 0) })
    }
    fun showReport(r: IpReport) {
        clear()
        val unknown = context.getString(R.string.ipcheck_unknown)
        fun known(s: String): String = s.trim().takeUnless { it.isEmpty() || it.equals("null", true) } ?: unknown
        val validScore = r.trustScore in 0..100
        val score = if (validScore) listOf("${r.trustScore} / 100", r.scoreLabel.trim()).filter { it.isNotEmpty() }.joinToString(" · ") else unknown
        val box = card()
        header(box, score)
        box.addView(text(known(r.ip), 19f, true).apply {
            setPadding(0, dp(8), 0, 0)
            setTextIsSelectable(true)
            if (android.os.Build.VERSION.SDK_INT >= 23) breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
        })
        val geo = listOf(r.country, r.region, r.city).map { it.trim() }.filter { it.isNotEmpty() && !it.equals("null", true) }.distinct().joinToString(" · ")
        box.addView(text(geo.ifEmpty { unknown }, 14f, secondary = true).apply { setPadding(0, dp(3), 0, dp(6)) })
        val native = when { r.isNative -> context.getString(R.string.ipcheck_native); r.isBroadcast -> context.getString(R.string.ipcheck_broadcast); else -> "${context.getString(R.string.ipcheck_native_label)}: $unknown" }
        val type = when { r.isDatacenter -> context.getString(R.string.ipcheck_datacenter); r.isResidential -> context.getString(R.string.ipcheck_residential); else -> "${context.getString(R.string.ipcheck_type)}: ${known(r.companyType)}" }
        box.addView(text("$native  ·  $type", 12f, secondary = true).apply {
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), color(com.google.android.material.R.attr.colorOutline).let { if (it == 0) color(android.R.attr.textColorSecondary) else it })
            }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        if (validScore) box.addView(ScoreScale(context, r.trustScore), LayoutParams(LayoutParams.MATCH_PARENT, dp(12)))
        row(box, context.getString(R.string.ipcheck_provider), known(listOf(r.companyName, r.asOrganization, r.isp).firstOrNull { it.isNotBlank() && !it.equals("null", true) } ?: ""))
        val asn = known(r.asn)
        val asnDisplay = if (asn == unknown) unknown else if (asn.startsWith("AS", true)) asn else "AS$asn"
        val asOrg = known(r.asOrganization)
        row(box, context.getString(R.string.ipcheck_asn), if (asOrg != unknown) "$asnDisplay · $asOrg" else asnDisplay)
        row(box, context.getString(R.string.ipcheck_flags), if (r.flags.isEmpty()) unknown else r.flags.joinToString(" · "))
        val human = r.humanPercent?.takeIf { it in 0..100 }
        if (r.isIpv6 && human == null && r.scenes.isEmpty()) {
            row(box, context.getString(R.string.ipcheck_human_scenes), context.getString(R.string.ipcheck_ipv6_na))
        } else {
            row(box, context.getString(R.string.ipcheck_human), if (human == null) unknown else context.getString(R.string.ipcheck_human_ratio, human, 100 - human))
            val scenes = listOf("tiktok" to R.string.ipcheck_scene_tiktok, "social" to R.string.ipcheck_scene_social, "ai" to R.string.ipcheck_scene_ai).map { (key, label) ->
                val value = r.scenes[key]?.score?.takeIf { it.isFinite() && it in 0.0..10.0 }
                "${context.getString(label)} ${if (value == null) unknown else String.format(Locale.ROOT, "%.0f/10", value)}"
            }.joinToString(" · ")
            row(box, context.getString(R.string.ipcheck_scenes), scenes)
            if (human != null) box.addView(text(context.getString(R.string.ipcheck_human_caveat), 11f, secondary = true).apply { setPadding(0, dp(3), 0, 0) })
            if (r.scenes.isNotEmpty()) box.addView(text(context.getString(R.string.ipcheck_scene_caveat), 11f, secondary = true))
        }
        val cc = r.countryCode.trim().uppercase(Locale.ROOT)
        val region = when {
            !cc.matches(Regex("[A-Z]{2}")) -> unknown
            r.aiRestricted -> context.getString(R.string.ipcheck_region_restricted, r.aiRestrictedName?.takeIf { it.isNotBlank() } ?: cc)
            else -> context.getString(R.string.ipcheck_region_not_listed)
        }
        row(box, context.getString(R.string.ipcheck_ai_region), region)
    }
}

private class ScoreScale(context: Context, private val score: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density; val left = 3*d; val right = width - 3*d; val y = height / 2f
        if (right <= left) return
        paint.shader = LinearGradient(left, 0f, right, 0f, intArrayOf(0xffc94641.toInt(), 0xffefbd49.toInt(), 0xff65c779.toInt(), 0xff228b48.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, y-2*d, right, y+2*d, 2*d, 2*d, paint); paint.shader = null
        val x = left + (right-left)*score/100f
        paint.color = Color.WHITE; paint.strokeWidth = 5*d; paint.strokeCap = Paint.Cap.ROUND; canvas.drawLine(x, y-4*d, x, y+4*d, paint)
        paint.color = Color.DKGRAY; paint.strokeWidth = 2*d; canvas.drawLine(x, y-4*d, x, y+4*d, paint)
    }
}
