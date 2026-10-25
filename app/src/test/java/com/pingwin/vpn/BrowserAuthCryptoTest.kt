package com.pingwin.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class BrowserAuthCryptoTest {
    private fun fixture() = javaClass.getResourceAsStream("/browser-auth-vector.json")!!.bufferedReader().use { JSONObject(it.readText()) }
    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
    private fun decrypt(json: JSONObject, state: String = json.getString("state")) = BrowserAuthCrypto.decrypt(
        Base64.getDecoder().decode(json.getString("privateKey")),decode(json.getString("key")),decode(json.getString("iv")),decode(json.getString("payload")),state)
    @Test fun decryptsActualBrowserWebCryptoPayload() {
        val result = JSONObject(decrypt(fixture()))
        assertEquals("fixture.access.token", result.getString("token"))
        assertEquals("fixture-refresh-token", result.getString("refresh_token"))
    }
    @Test fun rejectsDifferentNonce() { assertThrows(Exception::class.java) { decrypt(fixture(), "b".repeat(64)) } }
    @Test fun rejectsModifiedCiphertext() {
        val json = fixture(); val bytes=decode(json.getString("payload")); bytes[0]=(bytes[0].toInt() xor 1).toByte()
        json.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        assertThrows(Exception::class.java) { decrypt(json) }
    }
}
