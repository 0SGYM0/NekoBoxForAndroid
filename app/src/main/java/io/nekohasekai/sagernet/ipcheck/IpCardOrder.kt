package io.nekohasekai.sagernet.ipcheck

/** Persist only stable exit identifiers; invalid or older values fall back safely. */
object IpCardOrder {
    val default = listOf("DEFAULT", "CHATGPT", "CLAUDE")
    fun decode(value: String?): List<String> {
        val ids = value?.split(',') ?: return default
        return if (ids.size == default.size && ids.toSet() == default.toSet()) ids else default
    }
    fun move(order: List<String>, from: Int, to: Int): List<String> {
        if (order.size != default.size || order.toSet() != default.toSet() || from !in order.indices || to !in order.indices) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/** Results are keyed by identity, never by position or completion order. */
class IpCardStates<T> {
    private val results = mutableMapOf<String, Result<T>>()
    fun clear() = results.clear()
    fun put(id: String, result: Result<T>) { results[id] = result }
    fun get(id: String): Result<T>? = results[id]
}

object IpCardSummary {
    fun format(name: String, score: Int, label: String?, status: ServiceRegion.Status,
               unknown: String, regionUnknown: String, restricted: String): String {
        if (status == ServiceRegion.Status.RESTRICTED) return "$name $restricted"
        val number = score.takeIf { it in 0..100 }?.toString() ?: unknown
        val detail = listOfNotNull(label?.trim()?.takeIf { score in 0..100 && it.isNotEmpty() },
            regionUnknown.takeIf { status == ServiceRegion.Status.UNKNOWN }).joinToString(" · ")
        return "$name $number" + if (detail.isNotEmpty()) "\n$detail" else ""
    }
}
