package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class UidPackageCacheTest {
    @Test fun repeatedConnectionsAvoidPackageBinderCallsButRefreshAfterExpiry() {
        var time = 0L
        var calls = 0
        var installed = listOf("original.app")
        val cache = UidPackageCache({ time }, { calls++; installed })
        repeat(1000) { assertEquals(installed, cache.packages(42)) }
        assertEquals(1, calls)
        installed = listOf("replacement.app")
        time = 5000
        assertEquals(installed, cache.packages(42))
        assertEquals(2, calls)
    }

    @Test fun cacheIsBoundedAndDoesNotRememberMissingPackagesOrFailures() {
        var calls = 0
        val cache = UidPackageCache({ 0L }, { uid -> calls++; if (uid < 0) emptyList() else listOf("app.$uid") }, capacity = 2)
        cache.packages(1); cache.packages(2); cache.packages(3); cache.packages(1)
        assertEquals(4, calls)
        cache.packages(-1); cache.packages(-1)
        assertEquals(6, calls)
        var attempts = 0
        val failing = UidPackageCache({ 0L }, { if (++attempts == 1) error("temporary") else listOf("recovered") })
        assertTrue(runCatching { failing.packages(1) }.isFailure)
        assertEquals(listOf("recovered"), failing.packages(1))
    }
}
