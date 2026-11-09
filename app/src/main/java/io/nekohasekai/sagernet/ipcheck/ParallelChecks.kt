package io.nekohasekai.sagernet.ipcheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Each check publishes independently on the caller's coroutine context. */
internal suspend fun <K, V> parallelChecks(
    keys: List<K>,
    check: suspend (K) -> V,
    publish: (K, Result<V>) -> Unit,
) = coroutineScope {
    keys.forEach { key ->
        launch {
            val result = try {
                Result.success(check(key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            coroutineContext.ensureActive()
            publish(key, result)
        }
    }
}
