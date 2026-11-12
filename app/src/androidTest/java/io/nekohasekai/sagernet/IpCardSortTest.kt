package io.nekohasekai.sagernet

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.sagernet.ipcheck.IpCardOrder
import io.nekohasekai.sagernet.ui.IpExitCard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IpCardSortTest {
    @Test fun orderPersistsAndCardControlsSurviveReportRerender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext
            val context = ContextThemeWrapper(app, R.style.Theme_SagerNet)
            val root = LayoutInflater.from(context).inflate(R.layout.layout_ipcheck_panel, null, false)
            val cards = root.findViewById<LinearLayout>(R.id.ipcheck_cards)
            val card = root.findViewById<IpExitCard>(R.id.ipcheck_default)
            val prefs = app.getSharedPreferences("ipcheck_sort_test", 0)
            val order = IpCardOrder.move(IpCardOrder.default, 2, 0)
            prefs.edit().putString("order", order.joinToString(",")).commit()
            assertEquals(order, IpCardOrder.decode(prefs.getString("order", null)))
            var clicks = 0
            var longClicks = 0
            card.onSortClick = { clicks++ }
            card.onSortLongClick = { longClicks++; true }
            card.showState("Checking")
            fun findControl(v: View): View? {
                if (v is TextView && v.text.toString() == "☰") return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findControl(v.getChildAt(i))?.let { return it }
                return null
            }
            val control = findControl(card)
            assertNotNull(control)
            control!!.performClick()
            assertTrue(control.performLongClick())
            assertEquals(1, clicks)
            assertEquals(1, longClicks)
            assertEquals(4, cards.childCount)
            assertEquals(R.id.ipcheck_default, cards.getChildAt(0).id)
            prefs.edit().clear().commit()
        }
    }
}
