package io.nekohasekai.sagernet.ui

/** Insets are window-relative; coordinator bounds can already exclude some or all of them. */
internal object IpPanelGeometry {
    data class Bounds(val height: Int, val left: Int, val right: Int, val bottom: Int)

    fun calculate(width: Int, height: Int, x: Int, y: Int, windowWidth: Int, windowHeight: Int,
                  insetLeft: Int, insetTop: Int, insetRight: Int, insetBottom: Int): Bounds {
        val w = width.coerceAtLeast(0)
        val h = height.coerceAtLeast(0)
        val top = (insetTop - y).coerceIn(0, h)
        val bottom = (y + h - (windowHeight - insetBottom)).coerceIn(0, h - top)
        val left = (insetLeft - x).coerceIn(0, w)
        val right = (x + w - (windowWidth - insetRight)).coerceIn(0, w - left)
        return Bounds(h - top - bottom, left, right, bottom)
    }
}
