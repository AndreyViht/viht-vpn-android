package com.pingwin.vpn
import org.junit.Assert.*
import org.junit.Test

class VpnRecoveryPolicyTest {
    @Test fun disconnectOrNewRequestAlwaysCancelsRecovery() {
        assertFalse(VpnRecoveryPolicy.allowed(false,false,1,1))
        assertFalse(VpnRecoveryPolicy.allowed(true,true,1,1))
        assertFalse(VpnRecoveryPolicy.allowed(true,false,1,2))
    }
    @Test fun longSessionsHaveNoFiveMinuteOrFiveAttemptLimit() {
        repeat(100_000) { attempt ->
            assertTrue(VpnRecoveryPolicy.allowed(true,false,1,1))
            assertTrue(VpnRecoveryPolicy.delayMillis(attempt) in 1000L..30000L)
        }
        assertFalse(VpnRecoveryPolicy.failed(1));assertTrue(VpnRecoveryPolicy.failed(2))
    }
}
