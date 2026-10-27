package com.pingwin.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PaymentNetworkPolicyTest {
    private val key="vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com&type=ws&host=example.com&path=%2Fvpn"
    @Test fun paymentDomainsBypassVpnEvenWhenAllOptionalBypassIsOff() {
        val config=JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key),JSONObject().put("bypassRussia",false)))
        val rules=config.getJSONObject("route").getJSONArray("rules")
        val direct=(0 until rules.length()).map { rules.getJSONObject(it) }.single { it.optJSONArray("domain_suffix")?.toString()==JSONArray(PaymentNetworkPolicy.domains).toString() }
        assertEquals("direct",direct.getString("outbound"))
        assertEquals("proxy",config.getJSONObject("route").getString("final"))
        assertFalse(PaymentNetworkPolicy.domains.contains("ru"))
        val dns=config.getJSONObject("dns").getJSONArray("rules").getJSONObject(0)
        assertEquals("bootstrap",dns.getString("server"))
        assertEquals("hijack-dns",rules.getJSONObject(1).getString("action"))
    }
    @Test fun upgradingCachedConfigurationsIsIdempotentAndKeepsOtherRouting() {
        val config=JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key),JSONObject().put("allowLan",false)))
        PaymentNetworkPolicy.apply(config); val once=config.toString()
        PaymentNetworkPolicy.apply(config);assertEquals(once,config.toString())
        val rules=config.getJSONObject("route").getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("action")=="reject" })
    }
    @Test fun onlyTheOwnApplicationAndExplicitUserExceptionsAreExcluded() {
        val settings=JSONObject().put("bypassRules",JSONObject().put("apps",JSONArray(listOf("ru.anviht.vpn.test","example.bank"))))
        assertEquals(listOf("ru.anviht.vpn.test","example.bank"),PaymentNetworkPolicy.directPackages("ru.anviht.vpn.test",settings))
        assertEquals(listOf("ru.anviht.vpn.test"),PaymentNetworkPolicy.directPackages("ru.anviht.vpn.test",JSONObject()))
    }
}
