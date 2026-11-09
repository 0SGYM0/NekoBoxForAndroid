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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IpCheckFragment : BottomSheetDialogFragment() {

    private var statusText: TextView? = null
    private var defaultText: TextView? = null
    private var chatgptText: TextView? = null
    private var claudeText: TextView? = null
    private var swipe: SwipeRefreshLayout? = null

    private var autoChecked = false
    private var requestId = 0L
    private var checkJob: Job? = null

    override fun onDestroyView() {
        requestId++
        checkJob?.cancel()
        checkJob = null
        defaultText = null
        chatgptText = null
        claudeText = null
        statusText = null
        swipe = null
        super.onDestroyView()
    }

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
        val id = ++requestId
        checkJob?.cancel()
        if (!DataStore.serviceState.connected) {
            statusText?.setText(R.string.ipcheck_status_disconnected)
            swipe?.isRefreshing = false
            return
        }
        statusText?.setText(R.string.ipcheck_status_detecting)
        listOf(defaultText, chatgptText, claudeText).forEach { it?.setText(R.string.ipcheck_status_detecting) }
        checkJob = viewLifecycleOwner.lifecycleScope.launch {
            var failed = false
            IpChecker.detectEach { exit, result ->
                if (id == requestId && DataStore.serviceState.connected) {
                    val target = when (exit) {
                        IpChecker.Exit.DEFAULT -> defaultText
                        IpChecker.Exit.CHATGPT -> chatgptText
                        IpChecker.Exit.CLAUDE -> claudeText
                    }
                    val report = result.getOrNull()
                    if (report == null) failed = true
                    target?.text = if (report != null) renderReport(report)
                        else getString(R.string.ipcheck_status_error, getString(R.string.ipcheck_retry_hint))
                }
            }
            if (id == requestId && DataStore.serviceState.connected) {
                statusText?.setText(if (failed) R.string.ipcheck_status_partial else R.string.ipcheck_status_done)
                swipe?.isRefreshing = false
            } else if (id == requestId) {
                statusText?.setText(R.string.ipcheck_status_disconnected)
                swipe?.isRefreshing = false
            }
        }
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
