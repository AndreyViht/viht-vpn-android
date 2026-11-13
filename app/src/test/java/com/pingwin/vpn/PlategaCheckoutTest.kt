package com.pingwin.vpn
import org.junit.Assert.*
import org.junit.Test
class PlategaCheckoutTest {
 @Test fun onlyProviderHttpsEntryIsAccepted() {
  assertTrue(PlategaCheckout.isEntry("https://pay.platega.io/sbp-qr/p/example?method=2"))
  listOf("http://pay.platega.io/p/x", "https://pay.platega.io.evil.test/p/x", "https://evil.test/p/x", "https://user@pay.platega.io/p/x", "https://pay.platega.io:8443/p/x", "javascript:alert(1)").forEach { assertFalse(it, PlategaCheckout.isEntry(it)) }
 }
}
