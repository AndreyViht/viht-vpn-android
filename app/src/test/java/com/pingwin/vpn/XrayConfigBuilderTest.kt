package com.pingwin.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayConfigBuilderTest {
    private val id = "11111111-1111-4111-8111-111111111111"
    private fun source(transport: String = "ws") = VihtVpnConfig.build(
        ConnectionProfileParser.parse("vless://$id@example.com:443?security=tls&sni=example.com&type=$transport&path=%2Fvpn&host=example.com&serviceName=grpc"),
        JSONObject().put("tvBypassRussia", true).put("dnsAdblock", true)
    )

    @Test fun preservesWsGrpcTlsCredentialsAndLocalOnlyAuthenticatedSocks() {
        for (transport in listOf("ws", "grpc")) {
            val config = XrayConfigBuilder.build(source(transport), 10801, 10802, "user", "secret")
            val outbound = config.getJSONArray("outbounds").getJSONObject(0)
            assertEquals(id, outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getJSONArray("users").getJSONObject(0).getString("id"))
            assertEquals(transport, outbound.getJSONObject("streamSettings").getString("network"))
            assertEquals("tls", outbound.getJSONObject("streamSettings").getString("security"))
            assertFalse(outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings").getBoolean("allowInsecure"))
            val socks = config.getJSONArray("inbounds").getJSONObject(0)
            assertEquals("127.0.0.1", socks.getString("listen"))
            assertTrue(socks.getJSONObject("settings").getBoolean("udp"))
            assertEquals("password", socks.getJSONObject("settings").getString("auth"))
        }
    }

    @Test fun preservesDnsAdblockPaymentLanAndUdpRulesWithNoExternalGeoAssets() {
        val config = XrayConfigBuilder.build(source(), 10801, 10802, "user", "secret")
        assertTrue(config.getJSONObject("dns").getJSONArray("servers").toString().contains("94.140.14.14"))
        val rules = config.getJSONObject("routing").getJSONArray("rules")
        assertEquals("viht-health", rules.getJSONObject(0).getJSONArray("inboundTag").getString(0))
        assertTrue(rules.toString().contains("domain:yoomoney.ru"))
        assertTrue(rules.toString().contains("192.168.0.0/16"))
        assertFalse(config.toString().contains("geoip:"))
        assertFalse(config.toString().contains("geosite:"))
        assertTrue((0 until rules.length()).map { rules.getJSONObject(it) }.any { it.optString("network") == "udp" && it.optString("port") == "443" && it.optString("outboundTag") == "block" })
    }

    @Test fun rejectsUnmappedProtocolsAndConflictingListenPorts() {
        assertFalse(XrayConfigBuilder.supported(source().replace("\"type\":\"vless\"", "\"type\":\"tuic\"")))
        assertTrue(runCatching { XrayConfigBuilder.build(source(), 10801, 10801, "user", "secret") }.isFailure)
    }
}
