package com.pingwin.vpn

import java.io.IOException
import java.net.SocketTimeoutException

/** Bounded retries for the control plane, never for VPN user traffic or submitted writes. */
internal object ServiceRequestPolicy {
    const val CONNECT_TIMEOUT_MS = 4_000
    const val DEFAULT_READ_TIMEOUT_MS = 8_000

    fun <N : Any, T> execute(
        method: String,
        readTimeoutMs: Int,
        networks: () -> List<N>,
        clock: () -> Long,
        send: (N?, Int, Int, Long) -> T,
    ): T {
        val read = readTimeoutMs.coerceIn(1_000, 120_000)
        val deadline = clock() + CONNECT_TIMEOUT_MS + read
        val attempts = if (method.equals("GET", true) || method.equals("HEAD", true)) 2 else 1
        var failure: IOException? = null
        repeat(attempts) { attempt ->
            val remaining = (deadline - clock()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (remaining <= 0) throw failure ?: SocketTimeoutException("Service request deadline exceeded")
            // Re-read physical networks after a failure: Wi-Fi may have disappeared meanwhile.
            val available = networks()
            val network = available.getOrNull(attempt) ?: available.firstOrNull()
            try {
                return send(network, minOf(CONNECT_TIMEOUT_MS, remaining), minOf(read, remaining), deadline)
            } catch (error: IOException) {
                failure = error
                if (attempt == attempts - 1) throw error
            }
        }
        throw failure ?: IOException("Service request failed")
    }
}
