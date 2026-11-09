package io.nekohasekai.sagernet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class IpPanelGeometryTest {
    @Test fun edgeToEdgeAndFittedWindows() {
        assertEquals(IpPanelGeometry.Bounds(922, 0, 0, 48), IpPanelGeometry.calculate(500, 1000, 0, 0, 500, 1000, 0, 30, 0, 48))
        assertEquals(IpPanelGeometry.Bounds(922, 0, 0, 0), IpPanelGeometry.calculate(500, 922, 0, 30, 500, 1000, 0, 30, 0, 48))
    }
    @Test fun landscapeSplitScreenAndCutout() {
        assertEquals(IpPanelGeometry.Bounds(360, 40, 30, 20), IpPanelGeometry.calculate(800, 400, 0, 0, 800, 400, 40, 20, 30, 20))
        assertEquals(IpPanelGeometry.Bounds(250, 0, 0, 0), IpPanelGeometry.calculate(300, 250, 100, 40, 500, 400, 0, 24, 0, 24))
    }
    @Test fun tinyAndZeroWindowsNeverProduceNegativeBounds() {
        assertEquals(IpPanelGeometry.Bounds(0, 2, 0, 0), IpPanelGeometry.calculate(2, 1, 0, 0, 2, 1, 20, 20, 20, 20))
        assertEquals(IpPanelGeometry.Bounds(0, 0, 0, 0), IpPanelGeometry.calculate(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    }
}
