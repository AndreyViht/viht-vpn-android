package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class TunnelStartupVerifierTest {
    @Test fun retriesWhenMobileTunnelIsNotReadyOnFirstProbe() {
        var clock = 0L
        var attempts = 0
        assertTrue(TunnelStartupVerifier.awaitReady({ clock }, { false }, { clock += it }, {
            clock += 6L * it
            ++attempts == 2
        }))
        assertEquals(2, attempts)
        assertTrue(clock <= 45_000L)
    }

    @Test fun neverAcceptsAStoppedSession() {
        var cancelled = false
        assertFalse(TunnelStartupVerifier.awaitReady({ 0L }, { cancelled }, {}, {
            cancelled = true
            true
        }))
    }

    @Test fun boundedFailuresDoNotMarkTunnelConnected() {
        var clock = 0L
        assertFalse(TunnelStartupVerifier.awaitReady({ clock }, { false }, { clock += it }, {
            clock += 6L * it
            false
        }))
        assertTrue(clock <= 45_000L)
    }
}
