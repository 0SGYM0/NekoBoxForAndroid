package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/** Hands a downward drag to the panel only after the scroll view reaches its top. */
class PanelScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : NestedScrollView(context, attrs) {
    var onPanelDragStart: (() -> Unit)? = null
    var onPanelDrag: ((Float) -> Unit)? = null
    var onPanelDragEnd: ((Float) -> Unit)? = null
    var onPanelDragCancel: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var anchorX = 0f
    private var anchorY = 0f
    private var atTop = false
    private var dragging = false
    private var rejected = false
    private var childDisallows = false

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        childDisallows = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anchorX = event.rawX
                anchorY = event.rawY
                atTop = !canScrollVertically(-1)
                dragging = false
                rejected = false
                childDisallows = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                rejected = true
                if (dragging) {
                    dragging = false
                    onPanelDragCancel?.invoke()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    onPanelDrag?.invoke(anchorY - event.rawY)
                    return true
                }
                if (!rejected && !childDisallows) {
                    if (canScrollVertically(-1)) {
                        atTop = false
                    } else if (!atTop) {
                        // Rebase on arrival at the top; do not jump by the previous scroll distance.
                        atTop = true
                        anchorX = event.rawX
                        anchorY = event.rawY
                    } else {
                        val dx = event.rawX - anchorX
                        val dy = event.rawY - anchorY
                        if (dy > slop && dy > abs(dx)) {
                            val cancel = MotionEvent.obtain(event)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                            dragging = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            onPanelDragStart?.invoke()
                            onPanelDrag?.invoke(-dy)
                            return true
                        }
                        if (abs(dx) > slop && abs(dx) > abs(dy)) rejected = true
                        if (dy < -slop) { anchorX = event.rawX; anchorY = event.rawY }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) onPanelDragCancel?.invoke()
                    else onPanelDragEnd?.invoke(anchorY - event.rawY)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
