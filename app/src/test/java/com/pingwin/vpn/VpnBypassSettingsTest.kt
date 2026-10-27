package com.pingwin.vpn
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VpnBypassSettingsTest {
    @Test fun acceptsDomainAndUrlAndDeduplicates() {
        val settings=JSONObject().put("excludedDomains",JSONArray(listOf("https://Example.com/path","*.example.com","пример.рф")))
        assertEquals(listOf("example.com","xn--e1afmkfd.xn--p1ai"),VpnBypassSettings.domains(settings))
        assertThrows(IllegalArgumentException::class.java) { VpnBypassSettings.domain("https://user:secret@example.com") }
        assertThrows(IllegalArgumentException::class.java) { VpnBypassSettings.domain("ftp://example.com") }
    }
    @Test fun createsMatchingDirectDnsAndRoutingRules() {
        val profile=ConnectionProfileParser.parse("vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&type=ws&sni=example.com")
        val config=JSONObject(VihtVpnConfig.build(profile,JSONObject().put("bypassRussia",false).put("excludedDomains",JSONArray(listOf("example.org")))))
        assertTrue(config.getJSONObject("dns").getJSONArray("rules").toString().contains("example.org"))
        val rules=config.getJSONObject("route").getJSONArray("rules")
        assertEquals("sniff",rules.getJSONObject(0).getString("action"))
        assertEquals("hijack-dns",rules.getJSONObject(1).getString("action"))
        assertEquals("direct",rules.getJSONObject(2).getString("outbound"))
        assertTrue((0 until rules.length()).any {
            val rule=rules.getJSONObject(it)
            rule.optString("outbound")=="direct" && rule.optJSONArray("domain_suffix")?.toString()?.contains("example.org")==true
        })
    }
    @Test fun persistedRulesUseOneAtomicSettingsObject() {
        val rules=JSONObject().put("domains",JSONArray(listOf("example.org"))).put("apps",JSONArray(listOf("com.example.app")))
        val settings=JSONObject().put("bypassRules",rules)
        assertEquals(listOf("example.org"),VpnBypassSettings.domains(settings))
        assertEquals(listOf("com.example.app"),VpnBypassSettings.packages(settings))
        assertThrows(IllegalArgumentException::class.java) { VpnBypassSettings.domain("999.2.3.4") }
    }
    @Test fun validatesAndroidPackageNames() {
        assertEquals(listOf("com.example.app"),VpnBypassSettings.packages(JSONObject().put("excludedApps",JSONArray(listOf("com.example.app","com.example.app")))))
        assertThrows(IllegalArgumentException::class.java) { VpnBypassSettings.packages(JSONObject().put("excludedApps",JSONArray(listOf("../app")))) }
    }
}
