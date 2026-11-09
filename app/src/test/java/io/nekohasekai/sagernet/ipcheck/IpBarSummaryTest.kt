package io.nekohasekai.sagernet.ipcheck

import org.junit.Assert.assertEquals
import org.junit.Test

class IpBarSummaryTest {
    private fun assess(cc: String, score: Int) = ServiceRegion.assess(ServiceRegion.Service.CHATGPT, "1.2.3.4", cc, score)

    @Test fun validScoreAndLabelSurviveRestriction() {
        assertEquals(IpBarSummary.Parts(70, "良好", ServiceRegion.Bar.RESTRICTED), IpBarSummary.parts(70, " 良好 ", assess("HK", 70)))
        assertEquals(IpBarSummary.Parts(100, "纯净", ServiceRegion.Bar.NOT_LISTED), IpBarSummary.parts(100, "纯净", assess("US", 100)))
    }

    @Test fun unknownScoresAndRegionsAreNotInvented() {
        assertEquals(IpBarSummary.Parts(null, null, ServiceRegion.Bar.RESTRICTED), IpBarSummary.parts(-1, "良好", assess("HK", -1)))
        assertEquals(IpBarSummary.Parts(null, null, ServiceRegion.Bar.NOT_LISTED), IpBarSummary.parts(101, "良好", assess("US", 101)))
        assertEquals(IpBarSummary.Parts(70, "良好", ServiceRegion.Bar.UNKNOWN), IpBarSummary.parts(70, "良好", assess("", 70)))
        assertEquals(IpBarSummary.Parts(null, null, ServiceRegion.Bar.UNKNOWN), IpBarSummary.parts(null, null, null))
    }
}
