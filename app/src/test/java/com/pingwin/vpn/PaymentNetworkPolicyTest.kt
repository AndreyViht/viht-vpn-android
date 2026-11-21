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
        val dnsRules=config.getJSONObject("dns").getJSONArray("rules")
        val dns=(0 until dnsRules.length()).map { dnsRules.getJSONObject(it) }.single { it.optJSONArray("domain_suffix")?.toString()==JSONArray(PaymentNetworkPolicy.domains).toString() }
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
    @Test fun beelineUsesServerPolicyEvenWithRegionalBypassAndCachedDirectRules() {
        val config=JSONObject(VihtVpnConfig.build(ConnectionProfileParser.parse(key),JSONObject().put("bypassRussia",true)))
        val direct=JSONObject().put("domain_suffix",JSONArray(listOf("beeline.ru"))).put("action","route").put("outbound","direct")
        config.getJSONObject("route").getJSONArray("rules").put(2,direct)
        PaymentNetworkPolicy.apply(config);val once=config.toString();PaymentNetworkPolicy.apply(config);assertEquals(once,config.toString())
        val routes=config.getJSONObject("route").getJSONArray("rules")
        val rule=(0 until routes.length()).map { routes.getJSONObject(it) }.first { it.optJSONArray("domain_suffix")?.toString()==JSONArray(BeelineNetworkPolicy.domains).toString() }
        assertEquals("proxy",rule.getString("outbound"))
        val dns=config.getJSONObject("dns").getJSONArray("rules").getJSONObject(0)
        assertEquals("remote",dns.getString("server"))
        assertFalse(PaymentNetworkPolicy.directPackages("ru.anviht.vpn.test",JSONObject()).contains("ru.beeline.services"))
    }
    @Test fun ownApplicationAndExplicitUserExceptionsAreExcluded() {
        val settings=JSONObject().put("bypassRules",JSONObject().put("apps",JSONArray(listOf("ru.anviht.vpn.test","example.bank"))))
        assertEquals(listOf("ru.anviht.vpn.test","example.bank"),PaymentNetworkPolicy.directPackages("ru.anviht.vpn.test",settings))
        assertEquals(listOf("ru.anviht.vpn.test"),PaymentNetworkPolicy.directPackages("ru.anviht.vpn.test",JSONObject()))
    }
}
