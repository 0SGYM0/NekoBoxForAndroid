package io.nekohasekai.sagernet.ipcheck

/** Keep raw IP reputation separate from the service-region assessment. */
object IpBarSummary {
    data class Parts(val score: Int?, val label: String?, val region: ServiceRegion.Bar)

    fun parts(score: Int?, label: String?, assessment: ServiceRegion.Assessment?): Parts {
        val validScore = score?.takeIf { it in 0..100 }
        return Parts(validScore, label?.trim()?.takeIf { validScore != null && it.isNotEmpty() }, ServiceRegion.bar(assessment))
    }
}
