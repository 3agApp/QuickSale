package me.sourov.quicksale.data.sync

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retries a single request on a transient network error ([IOException]) with a short exponential
 * backoff. HTTP/auth failures surface as [me.sourov.quicksale.data.remote.WooApiException] and are
 * NOT retried — a wrong key, a missing plugin or a job the store refuses should fail fast rather
 * than spin.
 *
 * Shared by every sync path: a fair's wifi drops a packet or two whichever route is being called.
 */
internal suspend fun <T> retryOnNetworkBlip(
    attempts: Int = 3,
    initialDelayMs: Long = 700L,
    block: suspend () -> T,
): T {
    var delayMs = initialDelayMs
    repeat(attempts - 1) {
        try {
            return block()
        } catch (_: IOException) {
            delay(delayMs)
            delayMs *= 2
        }
    }
    return block() // last attempt: let the exception propagate
}
