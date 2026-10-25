package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class VpnStatusTest {
    @Test fun explicitStopMustNotScheduleAutomaticReconnect() {
        VpnStatus.set(VpnConnectionState.CONNECTING)
        VpnStatus.set(VpnConnectionState.CONNECTED)
        VpnStatus.markManualStop()
        VpnStatus.set(VpnConnectionState.DISCONNECTED)
        assertFalse(VpnStatus.snapshot().getBoolean("retryable"))
        assertEquals(0L, VpnStatus.connectedAt)
        VpnStatus.set(VpnConnectionState.CONNECTING)
        assertTrue(VpnStatus.retryable)
        VpnStatus.fail("Permission revoked", false)
        assertFalse(VpnStatus.retryable)
        assertEquals("Permission revoked", VpnStatus.snapshot().getString("error"))
        VpnStatus.set(VpnConnectionState.DISCONNECTED)
    }
}
