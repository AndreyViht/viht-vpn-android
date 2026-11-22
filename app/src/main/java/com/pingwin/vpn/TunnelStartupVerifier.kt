package com.pingwin.vpn

/** A listening local proxy is not yet evidence that the remote TLS tunnel is ready. */
internal object TunnelStartupVerifier {
    fun awaitReady(
        now: () -> Long,
        cancelled: () -> Boolean,
        pause: (Long) -> Unit,
        probe: (Int) -> Boolean,
        budgetMillis: Long = 25_000L,
    ): Boolean {
        val deadline = now() + budgetMillis
        while (!cancelled()) {
            val remaining = deadline - now()
            // Each probe can try three independent endpoints (connect and read timeouts).
            if (remaining < 1_500L) return false
            val timeout = (remaining / 6).coerceAtMost(2_500L).toInt()
            if (probe(timeout)) return !cancelled()
            if (cancelled() || deadline - now() < 1_750L) return false
            pause(250L)
        }
        return false
    }
}
