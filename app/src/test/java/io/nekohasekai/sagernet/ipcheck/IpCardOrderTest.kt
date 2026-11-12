package io.nekohasekai.sagernet.ipcheck

import org.junit.Assert.*
import org.junit.Test

class IpCardOrderTest {
    @Test fun orderRoundTripsAndRejectsCorruption() {
        val order = IpCardOrder.move(IpCardOrder.default, 2, 0)
        assertEquals(listOf("CLAUDE", "DEFAULT", "CHATGPT"), order)
        assertEquals(order, IpCardOrder.decode(order.joinToString(",")))
        assertEquals(IpCardOrder.default, IpCardOrder.decode("DEFAULT,DEFAULT,CLAUDE"))
        assertEquals(IpCardOrder.default, IpCardOrder.decode(null))
    }
    @Test fun resultsFollowIdentityAcrossCompletionOrderAndSelection() {
        val states = IpCardStates<Int>()
        states.put("CLAUDE", Result.success(42))
        states.put("CHATGPT", Result.failure(IllegalStateException()))
        assertNull(states.get("DEFAULT"))
        assertEquals(42, states.get("CLAUDE")?.getOrNull())
        assertTrue(states.get("CHATGPT")!!.isFailure)
        val order = IpCardOrder.move(IpCardOrder.default, 2, 0)
        assertEquals(42, states.get(order.first())?.getOrNull())
        states.put("DEFAULT", Result.success(99))
        assertEquals(42, states.get(order.first())?.getOrNull())
        states.clear()
        assertNull(states.get("CLAUDE"))
    }
    @Test fun restrictedNeverShowsScoreUnknownIsHonestAndDefaultIsIndependent() {
        fun format(name: String, status: ServiceRegion.Status) = IpCardSummary.format(name, 81, "良好", status, "未知", "地区未知", "受限")
        assertEquals("GPT 受限", format("GPT", ServiceRegion.Status.RESTRICTED))
        assertEquals("Claude 受限", format("Claude", ServiceRegion.Status.RESTRICTED))
        assertEquals("Claude 81\n良好 · 地区未知", format("Claude", ServiceRegion.Status.UNKNOWN))
        assertEquals("默认出口 81\n良好", format("默认出口", ServiceRegion.Status.NOT_APPLICABLE))
        assertEquals("GPT 81\n良好", format("GPT", ServiceRegion.Status.NOT_LISTED))
        assertEquals("GPT 未知", IpCardSummary.format("GPT", -1, "良好", ServiceRegion.Status.NOT_LISTED, "未知", "地区未知", "受限"))
    }
}
