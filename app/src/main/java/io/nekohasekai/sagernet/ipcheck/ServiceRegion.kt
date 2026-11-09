package io.nekohasekai.sagernet.ipcheck

import java.util.Locale

/** Snapshot of ip.net.coffee's separate page scripts, 2026-11-09; not an official allowlist. */
object ServiceRegion {
    enum class Service { DEFAULT, CHATGPT, CLAUDE }
    enum class Status { NOT_APPLICABLE, RESTRICTED, NOT_LISTED, UNKNOWN }
    data class Assessment(val status: Status, val siteScore: Int?)
    enum class Bar { RESTRICTED, NOT_LISTED, UNKNOWN }
    fun bar(assessment: Assessment?): Bar = when (assessment?.status) {
        Status.RESTRICTED -> Bar.RESTRICTED
        Status.NOT_LISTED -> Bar.NOT_LISTED
        else -> Bar.UNKNOWN
    }

    // Kept separate even though the two source snapshots currently match.
    private val chatGptRestricted = setOf("CN", "HK", "MO", "RU", "KP", "IR", "SY", "CU", "BY", "VE")
    private val claudeRestricted = setOf("CN", "HK", "MO", "RU", "KP", "IR", "SY", "CU", "BY", "VE")
    // ISO country/territory codes prevent malformed or invented codes being treated as not listed.
    private val countryCodes = Locale.getISOCountries().toSet()

    fun assess(service: Service, ip: String?, countryCode: String?, trustScore: Int?): Assessment {
        if (service == Service.DEFAULT) return Assessment(Status.NOT_APPLICABLE, null)
        val cc = countryCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (ip.isNullOrBlank() || ip.trim().equals("null", true) || cc !in countryCodes) {
            return Assessment(Status.UNKNOWN, null)
        }
        val restricted = when (service) {
            Service.CHATGPT -> chatGptRestricted
            Service.CLAUDE -> claudeRestricted
            Service.DEFAULT -> emptySet()
        }
        return if (cc in restricted) Assessment(Status.RESTRICTED, 0)
        else Assessment(Status.NOT_LISTED, trustScore?.takeIf { it in 0..100 })
    }
}
