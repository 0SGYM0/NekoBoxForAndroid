package io.nekohasekai.sagernet.ipcheck

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRegionTest {
    private val services = listOf(ServiceRegion.Service.CHATGPT, ServiceRegion.Service.CLAUDE)
    private fun check(service: ServiceRegion.Service, ip: String?, cc: String?, score: Int?, status: ServiceRegion.Status, result: Int?) {
        assertEquals(ServiceRegion.Assessment(status, result), ServiceRegion.assess(service, ip, cc, score))
    }
    @Test fun hongKongIsOverriddenWithoutChangingRawScore() {
        services.forEach { s ->
            check(s, "42.2.110.72", " hk ", 100, ServiceRegion.Status.RESTRICTED, 0)
            check(s, "42.2.110.72", "HK", null, ServiceRegion.Status.RESTRICTED, 0)
        }
    }
    @Test fun usPreservesValidScoresAndRejectsInvalidOnes() {
        services.forEach { s ->
            listOf(0, 1, 99, 100).forEach { n -> check(s, "1.2.3.4", "US", n, ServiceRegion.Status.NOT_LISTED, n) }
            listOf(-1, 101).forEach { n -> check(s, "1.2.3.4", "US", n, ServiceRegion.Status.NOT_LISTED, null) }
            check(s, "1.2.3.4", "US", null, ServiceRegion.Status.NOT_LISTED, null)
        }
    }
    @Test fun unknownNeverBecomesUnrestrictedOrScored() {
        services.forEach { s ->
            listOf(null, "", "null", "XX", "USA", "12").forEach { cc -> check(s, "1.2.3.4", cc, 100, ServiceRegion.Status.UNKNOWN, null) }
            listOf(null, "", "null", "  ").forEach { ip -> check(s, ip, "HK", 100, ServiceRegion.Status.UNKNOWN, null) }
        }
    }
    @Test fun barNeverShowsRawScoreInsteadOfRegionStatus() {
        val service = ServiceRegion.Service.CHATGPT
        assertEquals(ServiceRegion.Bar.RESTRICTED, ServiceRegion.bar(ServiceRegion.assess(service, "42.2.110.72", "HK", 100)))
        assertEquals(ServiceRegion.Bar.NOT_LISTED, ServiceRegion.bar(ServiceRegion.assess(service, "1.2.3.4", "US", 100)))
        assertEquals(ServiceRegion.Bar.UNKNOWN, ServiceRegion.bar(ServiceRegion.assess(service, "1.2.3.4", "", 100)))
        assertEquals(ServiceRegion.Bar.UNKNOWN, ServiceRegion.bar(null))
    }
    @Test fun defaultIsNeverSubjectToServiceRules() {
        check(ServiceRegion.Service.DEFAULT, "42.2.110.72", "HK", 100, ServiceRegion.Status.NOT_APPLICABLE, null)
    }
}
