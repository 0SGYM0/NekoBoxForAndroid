package io.nekohasekai.sagernet

import android.content.res.Configuration
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.ipcheck.IpReport
import io.nekohasekai.sagernet.ipcheck.ServiceRegion
import io.nekohasekai.sagernet.ui.IpExitCard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeInflationTest {
    // Mirrors the selectable built-in application themes in values/themes.xml.
    private val themes = listOf(
        "Pink", "Pink_SSR", "Amber", "Blue", "BlueGrey", "Brown", "Cyan", "DeepOrange",
        "DeepPurple", "Green", "Grey", "Indigo", "LightBlue", "LightGreen",
        "Lime", "Orange", "Purple", "Red", "Teal", "Yellow", "Black"
    )

    @Test fun inflateMainAndRenderCardsInEveryThemeAndNightMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val config = Configuration(app.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }
                val configured = app.createConfigurationContext(config)
                for (name in themes) {
                    val style = app.resources.getIdentifier(if (name == "Pink") "Theme.SagerNet" else "Theme.SagerNet.$name", "style", app.packageName)
                    assertNotEquals("missing theme $name", 0, style)
                    val context = ContextThemeWrapper(configured, style)
                    for (attr in listOf(com.google.android.material.R.attr.colorSurface,
                        com.google.android.material.R.attr.colorError, R.attr.colorMaterial300,
                        R.attr.whiteOrTextPrimary, android.R.attr.textColorPrimary,
                        android.R.attr.textColorSecondary)) {
                        val value = TypedValue()
                        assertTrue("unresolved attr $attr in $name night=$night", context.theme.resolveAttribute(attr, value, true))
                        val a = context.obtainStyledAttributes(intArrayOf(attr))
                        try { assertNotNull("not a color $attr in $name night=$night", a.getColorStateList(0)) }
                        finally { a.recycle() }
                    }
                    val root = try { LayoutInflater.from(context).inflate(R.layout.layout_main, null, false) }
                    catch (e: Exception) { throw AssertionError("inflate failed: $name night=$night", e) }
                    val notice = root.findViewById<TextView>(R.id.ipcheck_notice)
                    assertNotNull(notice)
                    assertTrue(notice.parent is MaterialCardView)
                    assertTrue(notice.text.isNotEmpty())
                    assertNotNull(root.findViewById<TextView>(R.id.ip_summary))
                    val card = root.findViewById<IpExitCard>(R.id.ipcheck_chatgpt)
                    card.showState("loading")
                    for (country in listOf("CN", "US", "ZZ", "")) {
                        val report = IpReport(
                            ip = "1.1.1.1", country = "Test", region = "", city = "", countryCode = country,
                            trustScore = 80, scoreLabel = "Good", isResidential = true, isDatacenter = false,
                            isNative = true, isBroadcast = false, companyType = "", companyName = "Test",
                            asn = "1", asOrganization = "Test", isp = "", humanPercent = null,
                            humanLabel = "", flags = emptyList(), isIpv6 = false, scenes = emptyMap(),
                            aiRestricted = false, aiRestrictedName = null
                        )
                        for (service in listOf(ServiceRegion.Service.CHATGPT, ServiceRegion.Service.CLAUDE)) {
                            card.showReport(report, service)
                            fun texts(view: android.view.View): List<String> =
                                (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
                                    (if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
                            val rendered = texts(card)
                            assertTrue(rendered.any { it.contains(context.getString(R.string.ipcheck_score)) && it.contains("80 / 100") })
                            assertFalse(rendered.contains(context.getString(R.string.ipcheck_site_score)))
                            assertFalse(rendered.contains(context.getString(R.string.ipcheck_service_not_listed)))
                            when (country) {
                                "CN" -> assertTrue(rendered.contains(context.getString(R.string.ipcheck_service_restricted, "CN")))
                                "US" -> assertFalse(rendered.contains(context.getString(R.string.ipcheck_service_unknown)))
                                else -> assertTrue(rendered.contains(context.getString(R.string.ipcheck_service_unknown)))
                            }
                        }
                    }
                }
            }
        }
    }
}
