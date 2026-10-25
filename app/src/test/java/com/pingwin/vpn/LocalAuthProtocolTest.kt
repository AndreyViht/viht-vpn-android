package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class LocalAuthProtocolTest {
    private fun parse(target: String = "/auth/callback?state=nonce", headers: String = "Origin: https://anviht.ru\r\nContent-Length: 2", method: String = "POST") =
        LocalAuthProtocol.read("$method $target HTTP/1.1\r\n$headers\r\n\r\n{}".byteInputStream())
    @Test fun validCallbackRequiresOriginNonceAndPost() {
        assertTrue(LocalAuthProtocol.authorized(parse(), "nonce"))
        assertFalse(LocalAuthProtocol.authorized(parse(), "other"))
        assertFalse(LocalAuthProtocol.authorized(parse(method = "GET"), "nonce"))
        assertFalse(LocalAuthProtocol.authorized(parse(headers = "Origin: https://evil.example\r\nContent-Length: 2"), "nonce"))
    }
    @Test fun rejectsAmbiguousAndUnboundedRequests() {
        listOf<() -> Unit>(
            { parse(headers = "Content-Length: garbage") },
            { parse(headers = "Content-Length: 999999") },
            { parse(headers = "Content-Length: 2\r\nContent-Length: 2") },
            { parse(headers = "Transfer-Encoding: chunked") },
            { parse(target = "/auth/callback?state=nonce&state=other") },
            { parse(target = "//evil.example/auth/callback?state=nonce") }
        ).forEach { action -> assertThrows(IllegalArgumentException::class.java) { action() } }
    }
}
