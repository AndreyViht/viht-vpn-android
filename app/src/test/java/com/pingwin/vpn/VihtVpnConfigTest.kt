package com.pingwin.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VihtVpnConfigTest {
    private val key = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=cdn.example.com&type=ws&host=cdn.example.com&path=%2Fvpn"
    @Test fun androidConfigPreservesTransportAndRoutesBothAddressFamilies() {
        val config = JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key), JSONObject()))
        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        assertTrue(tun.getJSONArray("address").toString().contains("fdfe:"))
        val outbound = config.getJSONArray("outbounds").getJSONObject(1)
        assertEquals("cdn.example.com", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("ws", outbound.getJSONObject("transport").getString("type"))
        assertEquals("/vpn", outbound.getJSONObject("transport").getString("path"))
        assertEquals("hijack-dns", config.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getString("action"))
    }
    @Test fun settingsChangeDnsAndLanPolicy() {
        val config = JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key), JSONObject().put("allowLan",false).put("bypassRussia",false).put("dnsAdblock",false)))
        assertEquals("reject", config.getJSONObject("route").getJSONArray("rules").getJSONObject(1).getString("action"))
        assertEquals(0, config.getJSONObject("dns").getJSONArray("rules").length())
        assertEquals("1.1.1.1", config.getJSONObject("dns").getJSONArray("servers").getJSONObject(1).getString("server"))
        val filtered = JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key), JSONObject()))
        assertEquals("94.140.14.14", filtered.getJSONObject("dns").getJSONArray("servers").getJSONObject(1).getString("server"))
    }
}
