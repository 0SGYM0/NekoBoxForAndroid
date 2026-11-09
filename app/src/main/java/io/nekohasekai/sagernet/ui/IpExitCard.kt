package io.nekohasekai.sagernet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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

/** A presentation-only view: no network calls or diagnostic data logging. */
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
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun section(title: String): LinearLayout {
        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat(); cardElevation = dp(1).toFloat()
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
            strokeWidth = dp(1); strokeColor = color(com.google.android.material.R.attr.colorOutline).let { if (it == 0) color(android.R.attr.textColorSecondary) else it }
        }
        val box = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(text(title, 13f, true, true).apply { setPadding(0, 0, 0, dp(10)) })
        card.addView(box)
        content.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        return box
    }
    private fun row(box: LinearLayout, label: String, value: String) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP; setPadding(0, dp(5), 0, dp(5)) }
        line.addView(text(label, 14f, secondary = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        line.addView(text(value, 14f).apply { gravity = Gravity.END }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.4f))
        box.addView(line)
    }
    fun clear() { content.removeAllViews() }
    fun showState(message: String) {
        clear()
        val box = section(context.getString(R.string.ipcheck_title))
        box.addView(text(message, 14f, secondary = true))
    }
    fun showReport(r: IpReport) {
        clear()
        val unknown = context.getString(R.string.ipcheck_unknown)
        fun known(s: String): String = s.trim().takeUnless { it.isEmpty() || it.equals("null", true) } ?: unknown
        val identity = section(context.getString(R.string.ipcheck_exit_ip))
        identity.addView(text(known(r.ip), 22f, true).apply {
            setTextIsSelectable(true)
            if (android.os.Build.VERSION.SDK_INT >= 23) breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
        })
        val geo = listOf(r.country, r.region, r.city).map { it.trim() }.filter { it.isNotEmpty() && !it.equals("null", true) }.distinct().joinToString(" · ")
        identity.addView(text(geo.ifEmpty { unknown }, 14f, secondary = true).apply { setPadding(0, dp(5), 0, 0) })

        val scoreBox = section(context.getString(R.string.ipcheck_score))
        val validScore = r.trustScore in 0..100
        val score = text(if (validScore) "${r.trustScore} / 100" else unknown, 28f, true)
        if (validScore) score.setTextColor(when { r.trustScore >= 80 -> Color.rgb(35, 145, 85); r.trustScore >= 50 -> Color.rgb(177, 125, 15); else -> Color.rgb(200, 69, 67) })
        scoreBox.addView(score)
        scoreBox.addView(ScoreScale(context, if (validScore) r.trustScore else null), LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        val ticks = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf("0", "25", "50", "75", "100").forEachIndexed { index, s ->
            ticks.addView(text(s, 11f, secondary = true).apply { gravity = when (index) { 0 -> Gravity.START; 4 -> Gravity.END; else -> Gravity.CENTER } }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        scoreBox.addView(ticks)
        if (validScore && r.scoreLabel.isNotBlank()) scoreBox.addView(text(r.scoreLabel, 13f, secondary = true).apply { setPadding(0, dp(7), 0, 0) })

        val details = section(context.getString(R.string.ipcheck_details))
        row(details, context.getString(R.string.ipcheck_native_label), when { r.isNative -> context.getString(R.string.ipcheck_native); r.isBroadcast -> context.getString(R.string.ipcheck_broadcast); else -> unknown })
        row(details, context.getString(R.string.ipcheck_type), when { r.isDatacenter -> context.getString(R.string.ipcheck_datacenter); r.isResidential -> context.getString(R.string.ipcheck_residential); else -> known(r.companyType) })
        row(details, context.getString(R.string.ipcheck_provider), known(listOf(r.companyName, r.asOrganization, r.isp).firstOrNull { it.isNotBlank() && !it.equals("null", true) } ?: ""))
        row(details, context.getString(R.string.ipcheck_asn), known(r.asn))
        row(details, context.getString(R.string.ipcheck_flags), if (r.flags.isEmpty()) unknown else r.flags.joinToString(" · "))

        val traffic = section(context.getString(R.string.ipcheck_human))
        val human = r.humanPercent?.takeIf { !r.isIpv6 && it in 0..100 }
        if (r.isIpv6) traffic.addView(text(context.getString(R.string.ipcheck_ipv6_na), 14f, secondary = true))
        else if (human == null) traffic.addView(text(unknown, 14f, secondary = true))
        else {
            traffic.addView(text(context.getString(R.string.ipcheck_human_ratio, human, 100 - human), 14f, true))
            traffic.addView(RatioBar(context, human), LayoutParams(LayoutParams.MATCH_PARENT, dp(18)))
            traffic.addView(text(context.getString(R.string.ipcheck_human_caveat), 12f, secondary = true))
        }
        val scenes = section(context.getString(R.string.ipcheck_scenes))
        if (r.isIpv6) scenes.addView(text(context.getString(R.string.ipcheck_ipv6_na), 14f, secondary = true))
        else {
            listOf("tiktok" to R.string.ipcheck_scene_tiktok, "social" to R.string.ipcheck_scene_social, "ai" to R.string.ipcheck_scene_ai).forEach { (key, label) ->
                val value = r.scenes[key]?.score?.takeIf { it.isFinite() && it in 0.0..10.0 }
                row(scenes, context.getString(label), if (value == null) unknown else String.format(Locale.ROOT, "%.0f / 10", value))
            }
            scenes.addView(text(context.getString(R.string.ipcheck_scene_caveat), 12f, secondary = true).apply { setPadding(0, dp(8), 0, 0) })
        }
    }
}

private class ScoreScale(context: Context, private val score: Int?) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density; val left = 3*d; val right = width - 3*d; val y = height / 2f
        if (right <= left) return
        paint.shader = LinearGradient(left, 0f, right, 0f, intArrayOf(0xffc94641.toInt(), 0xffefbd49.toInt(), 0xff65c779.toInt(), 0xff228b48.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, y-3*d, right, y+3*d, 3*d, 3*d, paint); paint.shader = null
        score?.let {
            val x = left + (right-left)*it/100f
            paint.color = Color.WHITE; paint.strokeWidth = 6*d; paint.strokeCap = Paint.Cap.ROUND; canvas.drawLine(x, y-7*d, x, y+7*d, paint)
            paint.color = Color.DKGRAY; paint.strokeWidth = 3*d; canvas.drawLine(x, y-7*d, x, y+7*d, paint)
        }
    }
}
private class RatioBar(context: Context, private val human: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val d = resources.displayMetrics.density; val y = height/2f
        paint.color = 0xffd55071.toInt(); canvas.drawRoundRect(0f, y-3*d, width.toFloat(), y+3*d, 3*d, 3*d, paint)
        paint.color = 0xff29854f.toInt(); canvas.drawRoundRect(0f, y-3*d, width*human/100f, y+3*d, 3*d, 3*d, paint)
    }
}
