package com.pingwin.vpn

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class ServiceRequestPolicyTest {
    @Test fun mobileOnlyUsesPhysicalNetworkAndRetriesTimeoutWithoutSystemSettings() {
        var calls = 0
        val result = ServiceRequestPolicy.execute("GET", 8000, { listOf("cellular") }, { 0L }) { network, _, _, _ ->
            assertEquals("cellular", network)
            if (++calls == 1) throw SocketTimeoutException("stalled")
            "three keys"
        }
        assertEquals("three keys", result); assertEquals(2, calls)
    }
    @Test fun networkHandoverIsObservedOnRetry() {
        var reads = 0; val used = mutableListOf<String?>()
        ServiceRequestPolicy.execute("GET", 8000, { if (++reads == 1) listOf("wifi") else listOf("cellular") }, { 0L }) { network, _, _, _ ->
            used.add(network); if (used.size == 1) throw IOException("Wi-Fi lost"); true
        }
        assertEquals(listOf("wifi", "cellular"), used)
    }
    @Test fun submittedWriteIsNeverAutomaticallyReplayed() {
        var calls = 0
        try {
            ServiceRequestPolicy.execute("POST", 8000, { listOf("cellular") }, { 0L }) { _, _, _, _ -> calls++; throw IOException("response lost") }
            fail("Write must fail without being sent twice")
        } catch (_: IOException) { assertEquals(1, calls) }
    }
    @Test fun retriesShareOneDeadline() {
        var now = 0L; var calls = 0
        ServiceRequestPolicy.execute("GET", 8000, { listOf("cellular") }, { now }) { _, connect, read, deadline ->
            assertEquals(12000L, deadline)
            if (++calls == 1) { now = 10000; throw IOException("slow connection") }
            assertEquals(2000, connect); assertEquals(2000, read); true
        }
        assertEquals(2, calls)
    }
    @Test fun exhaustedDeadlineDoesNotStartAnotherRequest() {
        var now = 0L; var calls = 0
        try {
            ServiceRequestPolicy.execute("GET", 8000, { listOf("cellular") }, { now }) { _, _, _, _ -> calls++; now = 12001; throw SocketTimeoutException() }
            fail("Deadline must be enforced")
        } catch (_: IOException) { assertEquals(1, calls) }
    }
    @Test fun httpRejectionIsReturnedWithoutRetryOrAuthBypass() {
        var calls = 0
        val status = ServiceRequestPolicy.execute("GET", 8000, { listOf("cellular") }, { 0L }) { _, _, _, _ -> calls++; 403 }
        assertEquals(403, status); assertEquals(1, calls)
    }
}
