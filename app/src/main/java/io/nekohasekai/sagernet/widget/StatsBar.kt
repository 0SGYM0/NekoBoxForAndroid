package io.nekohasekai.sagernet.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.google.android.material.bottomappbar.BottomAppBar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StatsBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.bottomAppBarStyle,
) : BottomAppBar(context, attrs, defStyleAttr) {
    private lateinit var statusText: TextView
    private lateinit var txText: TextView
    private lateinit var rxText: TextView
    private lateinit var ipSummaryText: TextView
    private lateinit var behavior: YourBehavior

    var allowShow = true

    /** 位移以物理像素计：向上为正、向下为负。开始回调用于记录面板当前位移。 */
    var onSwipeStart: (() -> Unit)? = null
    var onSwipeUp: ((dy: Float) -> Unit)? = null
    var onSwipeUpEnd: ((dy: Float) -> Unit)? = null
    /** 系统取消手势时应恢复开始时的面板状态，不应触发检测。 */
    var onSwipeCancel: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var gestureRejected = false

    override fun getBehavior(): YourBehavior {
        if (!this::behavior.isInitialized) behavior = YourBehavior { allowShow }
        return behavior
    }

    class YourBehavior(val getAllowShow: () -> Boolean) : Behavior() {

        override fun onNestedScroll(
            coordinatorLayout: CoordinatorLayout, child: BottomAppBar, target: View,
            dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed + dyUnconsumed,
                dxUnconsumed,
                0,
                type,
                consumed
            )
        }

        override fun slideUp(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideUp(child)
        }

        override fun slideDown(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideDown(child)
        }
    }


    override fun onFinishInflate() {
        super.onFinishInflate()
        statusText = findViewById(R.id.status)
        txText = findViewById(R.id.tx)
        rxText = findViewById(R.id.rx)
        ipSummaryText = findViewById(R.id.ip_summary)
    }

    /**
     * 面板头部手柄复用底栏的同一组回调；滚动内容不绑定此方法，避免抢占内容滚动。
     * 调用方负责根据面板初始状态解释正负位移并处理展开、收起和取消。
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setPanelDragHandle(handle: View) {
        handle.setOnTouchListener { _, event ->
            handleDrag(event)
            true
        }
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                swiping = false
                gestureRejected = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (swiping) onSwipeCancel?.invoke()
                swiping = false
                gestureRejected = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = downY - event.rawY
                if (!swiping && !gestureRejected && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    if (kotlin.math.abs(dy) > touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx) && onSwipeUp != null) {
                        swiping = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onSwipeStart?.invoke()
                    } else {
                        gestureRejected = true
                    }
                }
                if (swiping) onSwipeUp?.invoke(dy)
            }
            MotionEvent.ACTION_UP -> {
                val wasSwiping = swiping
                if (wasSwiping) onSwipeUpEnd?.invoke(downY - event.rawY)
                swiping = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return wasSwiping
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasSwiping = swiping
                if (wasSwiping) onSwipeCancel?.invoke()
                swiping = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return wasSwiping
            }
        }
        return swiping
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wasSwiping = swiping
        val handledDrag = handleDrag(event)
        if (!wasSwiping && handledDrag) {
            // 清除 BottomAppBar 的 pressed/click 状态，避免滑动松手触发测速。
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.onTouchEvent(cancel)
            cancel.recycle()
        }
        if (wasSwiping || handledDrag) return true
        return super.onTouchEvent(event)
    }

    /** 更新右侧 IP 摘要（如「GPT 81 纯净」）；传空字符串则隐藏 */
    fun updateIpSummary(text: CharSequence) {
        if (!this::ipSummaryText.isInitialized) return
        if (text.isEmpty()) {
            ipSummaryText.visibility = View.INVISIBLE
        } else {
            ipSummaryText.visibility = View.VISIBLE
            ipSummaryText.text = text
            ipSummaryText.contentDescription = text
        }
    }

    private fun setStatus(text: CharSequence) {
        statusText.text = text
        statusText.contentDescription = text
        TooltipCompat.setTooltipText(this, text)
    }

    fun changeState(state: BaseService.State) {
        val activity = context as MainActivity
        fun postWhenStarted(what: () -> Unit) = activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(100L)
            activity.whenStarted { what() }
        }
        if ((state == BaseService.State.Connected).also { hideOnScroll = it }) {
            postWhenStarted {
                if (allowShow) performShow()
                setStatus(app.getText(R.string.vpn_connected))
            }
        } else {
            postWhenStarted {
                performHide()
            }
            updateSpeed(0, 0)
            setStatus(
                context.getText(
                    when (state) {
                        BaseService.State.Connecting -> R.string.connecting
                        BaseService.State.Stopping -> R.string.stopping
                        else -> R.string.not_connected
                    }
                )
            )
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateSpeed(txRate: Long, rxRate: Long) {
        txText.text = "▲  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, txRate)
            )
        }"
        txText.contentDescription = txText.text
        rxText.text = "▼  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, rxRate)
            )
        }"
        rxText.contentDescription = rxText.text
    }

    fun testConnection() {
        val activity = context as MainActivity
        isEnabled = false
        setStatus(app.getText(R.string.connection_test_testing))
        runOnDefaultDispatcher {
            try {
                val elapsed = activity.urlTest()
                onMainDispatcher {
                    isEnabled = true
                    setStatus(
                        app.getString(
                            if (DataStore.connectionTestURL.startsWith("https://")) {
                                R.string.connection_test_available
                            } else {
                                R.string.connection_test_available_http
                            }, elapsed
                        )
                    )
                }

            } catch (e: Exception) {
                Logs.w(e.toString())
                onMainDispatcher {
                    isEnabled = true
                    setStatus(app.getText(R.string.connection_test_testing))

                    activity.snackbar(
                        app.getString(
                            R.string.connection_test_error, e.readableMessage
                        )
                    ).show()
                }
            }
        }
    }

}
