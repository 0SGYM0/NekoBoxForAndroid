package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ipcheck.IpChecker
import io.nekohasekai.sagernet.ipcheck.IpReport
import io.nekohasekai.sagernet.ipcheck.SceneScore
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnLifecycleDispatcher

class IpCheckFragment : BottomSheetDialogFragment() {

    private var statusText: TextView? = null
    private var defaultText: TextView? = null
    private var chatgptText: TextView? = null
    private var claudeText: TextView? = null
    private var swipe: SwipeRefreshLayout? = null

    private var autoChecked = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.layout_ipcheck, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.ipcheck_status)
        defaultText = view.findViewById(R.id.ipcheck_default)
        chatgptText = view.findViewById(R.id.ipcheck_chatgpt)
        claudeText = view.findViewById(R.id.ipcheck_claude)
        swipe = view.findViewById(R.id.ipcheck_swipe)

        swipe?.setOnRefreshListener { startDetect() }
    }

    override fun onStart() {
        super.onStart()
        // 默认展开到较高位置，且可拖拽收起
        val behavior = (dialog as? BottomSheetDialog)?.behavior
        behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!autoChecked) {
            autoChecked = true
            if (DataStore.serviceState.connected) {
                startDetect()
            } else {
                statusText?.setText(R.string.ipcheck_status_disconnected)
            }
        }
    }

    private fun startDetect() {
        if (!DataStore.serviceState.connected) {
            statusText?.setText(R.string.ipcheck_status_disconnected)
            swipe?.isRefreshing = false
            return
        }
        statusText?.setText(R.string.ipcheck_status_detecting)
        runOnLifecycleDispatcher {
            try {
                val result = IpChecker.detect()
                onMainDispatcher {
                    render(result)
                    statusText?.setText(R.string.ipcheck_status_done)
                    swipe?.isRefreshing = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    statusText?.text = getString(R.string.ipcheck_status_error, e.message ?: "")
                    swipe?.isRefreshing = false
                }
            }
        }
    }

    private fun render(result: IpChecker.DetectResult) {
        defaultText?.text = renderReport(result.default)
        chatgptText?.text = renderReport(result.chatgpt)
        claudeText?.text = renderReport(result.claude)
    }

    private fun renderReport(r: IpReport): String {
        val sb = StringBuilder()
        sb.append(r.ip).append('\n')

        val geo = listOf(r.country, r.region, r.city).filter { it.isNotBlank() }.joinToString(" · ")
        if (geo.isNotBlank()) {
            sb.append(getString(R.string.ipcheck_geo)).append(' ').append(geo).append('\n')
        }

        if (r.trustScore >= 0) {
            sb.append(getString(R.string.ipcheck_score))
                .append(' ').append(r.trustScore)
                .append(' ').append(r.scoreLabel).append('\n')
        }

        if (r.isNative || r.isBroadcast) {
            val nativeText = if (r.isNative) getString(R.string.ipcheck_native)
            else getString(R.string.ipcheck_broadcast)
            sb.append(getString(R.string.ipcheck_native_label)).append(' ').append(nativeText).append('\n')
        }
        if (r.isDatacenter) {
            sb.append(getString(R.string.ipcheck_datacenter)).append('\n')
        } else if (r.isResidential) {
            sb.append(getString(R.string.ipcheck_residential)).append('\n')
        }

        val org = listOf(r.companyName, r.isp).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        if (org.isNotBlank()) {
            sb.append(getString(R.string.ipcheck_provider)).append(' ').append(org).append('\n')
        }
        if (r.asn.isNotBlank()) {
            val asnText = if (r.asOrganization.isNotBlank()) "AS${r.asn} · ${r.asOrganization}" else "AS${r.asn}"
            sb.append(getString(R.string.ipcheck_asn)).append(' ').append(asnText).append('\n')
        }

        if (r.humanPercent != null) {
            val bot = 100 - r.humanPercent
            sb.append(getString(R.string.ipcheck_human))
                .append(" 人类 ").append(r.humanPercent).append("% / 机器 ").append(bot).append("%")
                .append("（").append(r.humanLabel).append("）").append('\n')
        } else if (r.humanLabel.isNotBlank()) {
            sb.append(getString(R.string.ipcheck_human)).append(' ').append(r.humanLabel).append('\n')
        }

        if (r.flags.isNotEmpty()) {
            sb.append(getString(R.string.ipcheck_flags)).append(' ').append(r.flags.joinToString("、")).append('\n')
        }

        if (r.scenes.isNotEmpty()) {
            val sceneParts = listOf("tiktok", "social", "ai").mapNotNull { key ->
                r.scenes[key]?.let { renderScene(key, it) }
            }
            if (sceneParts.isNotEmpty()) {
                sb.append(getString(R.string.ipcheck_scenes)).append(' ').append(sceneParts.joinToString(" · ")).append('\n')
            }
        } else if (r.isIpv6) {
            sb.append(getString(R.string.ipcheck_scenes)).append(' ').append(getString(R.string.ipcheck_ipv6_na)).append('\n')
        }

        sb.append(getString(R.string.ipcheck_ai_region))
            .append(' ').append(if (r.aiRestricted) "受限（${r.aiRestrictedName ?: r.countryCode}）" else "正常")
            .append('\n')

        return sb.toString().trimEnd()
    }

    private fun renderScene(key: String, s: SceneScore): String {
        val name = when (key) {
            "tiktok" -> getString(R.string.ipcheck_scene_tiktok)
            "social" -> getString(R.string.ipcheck_scene_social)
            else -> getString(R.string.ipcheck_scene_ai)
        }
        return "$name ${s.verdict}"
    }

    companion object {
        fun show(activity: MainActivity) {
            IpCheckFragment().show(activity.supportFragmentManager, "ipcheck")
        }
    }
}
