package io.nekohasekai.sagernet

import android.content.res.Configuration
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.sagernet.widget.PanelScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanelGestureTest {
    @Test fun headerAndBottomBarFitAcrossSizesAndFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext
            for (scale in listOf(1f, 1.5f, 2f)) {
                val cfg = Configuration(app.resources.configuration).apply { fontScale = scale }
                val context = ContextThemeWrapper(app.createConfigurationContext(cfg), R.style.Theme_SagerNet)
                val root = LayoutInflater.from(context).inflate(R.layout.layout_main, null, false)
                val panel = root.findViewById<View>(R.id.ipcheck_panel)
                val stats = root.findViewById<View>(R.id.stats)
                val summary = root.findViewById<TextView>(R.id.ip_summary)
                val status = root.findViewById<TextView>(R.id.status)
                summary.visibility = View.VISIBLE
                summary.text = "GPT 100\n优质 ⚠ 地区受限"
                status.text = "连接成功: HTTPS 握手耗时 123456ms"
                for ((width, height) in listOf(320 to 600, 800 to 360)) {
                    val w = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                    panel.measure(w, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    panel.layout(0, 0, width, height)
                    val refresh = panel.findViewById<ImageButton>(R.id.ipcheck_refresh)
                    assertEquals(context.getString(R.string.ipcheck_refresh), refresh.contentDescription)
                    assertTrue(refresh.width >= (48 * context.resources.displayMetrics.density).toInt())
                    assertNotNull(refresh.drawable)
                    status.text = "连接成功: HTTPS 握手耗时 123456ms"
                    stats.measure(w, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    stats.layout(0, 0, width, stats.measuredHeight)
                    assertEquals(1, status.maxLines)
                    assertEquals(2, summary.maxLines)
                    val metrics = summary.paint.fontMetricsInt
                    val fontPadding = (metrics.bottom - metrics.top) - (metrics.descent - metrics.ascent)
                    assertTrue(summary.height <= summary.lineHeight * 2 + fontPadding + summary.paddingTop + summary.paddingBottom)
                    val beforeHeight = stats.measuredHeight
                    status.text = "检测中"
                    stats.measure(w, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    assertEquals(beforeHeight, stats.measuredHeight)
                }
            }
        }
    }

    @Test fun downwardAtTopDragsAndCancellationRestoresWhileScrollStillWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext
            val context = ContextThemeWrapper(app, R.style.Theme_SagerNet)
            val panel = LayoutInflater.from(context).inflate(R.layout.layout_ipcheck_panel, null, false)
            val scroll = panel.findViewById<PanelScrollView>(R.id.ipcheck_scroll)
            val width = (320 * context.resources.displayMetrics.density).toInt()
            val height = (500 * context.resources.displayMetrics.density).toInt()
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, width, height)
            var starts = 0
            var ends = 0
            var cancels = 0
            var distance = 0f
            scroll.onPanelDragStart = { starts++ }
            scroll.onPanelDrag = { distance = it }
            scroll.onPanelDragEnd = { ends++ }
            scroll.onPanelDragCancel = { cancels++ }
            val t = SystemClock.uptimeMillis()
            fun send(action: Int, y: Float, time: Long) {
                val e = MotionEvent.obtain(t, t + time, action, 50f, y, 0)
                scroll.dispatchTouchEvent(e)
                e.recycle()
            }
            scroll.scrollTo(0, 0)
            send(MotionEvent.ACTION_DOWN, 50f, 0)
            send(MotionEvent.ACTION_MOVE, 150f, 30)
            assertEquals(1, starts)
            assertTrue(distance < 0f)
            send(MotionEvent.ACTION_CANCEL, 150f, 40)
            assertEquals(1, cancels)
            assertEquals(0, ends)
            send(MotionEvent.ACTION_DOWN, 50f, 100)
            send(MotionEvent.ACTION_MOVE, 150f, 130)
            send(MotionEvent.ACTION_UP, 150f, 140)
            assertEquals(2, starts)
            assertEquals(1, ends)
            // A drag starting below the top must remain a scroll, not close the panel.
            val content = scroll.getChildAt(0)
            if (content.height > scroll.height) {
                scroll.scrollTo(0, content.height - scroll.height)
                send(MotionEvent.ACTION_DOWN, 50f, 200)
                send(MotionEvent.ACTION_MOVE, 100f, 230)
                send(MotionEvent.ACTION_UP, 100f, 240)
                assertEquals(2, starts)
            }
        }
    }
}
