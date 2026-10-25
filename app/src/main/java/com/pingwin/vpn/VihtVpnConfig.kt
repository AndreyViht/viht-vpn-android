package com.pingwin.vpn

import org.json.JSONArray
import org.json.JSONObject

object VihtVpnConfig {
    fun build(profile: ConnectionProfile, settings: JSONObject): String {
        val routing = RoutingSettings(bypassRussiaSites = settings.optBoolean("bypassRussia", true))
        val config = JSONObject(ConnectionConfigBuilder.build(profile, routing, false))
        val bootstrap = JSONObject().put("type", "udp").put("tag", "bootstrap").put("server", "1.1.1.1").put("detour", "direct")
        val remote = JSONObject().put("type", if (settings.optBoolean("dnsAdblock", true)) "https" else "udp")
            .put("tag", "remote").put("server", "1.1.1.1").put("detour", "proxy")
        val dnsRules = JSONArray()
        if (routing.bypassRussiaSites) dnsRules.put(JSONObject().put("domain_suffix", JSONArray(listOf("ru", "su", "xn--p1ai", "yandex.net", "vk.com"))).put("server", "bootstrap"))
        config.put("dns", JSONObject().put("servers", JSONArray().put(bootstrap).put(remote)).put("rules", dnsRules).put("final", "remote").put("strategy", "prefer_ipv4"))
        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        tun.put("address", JSONArray(listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126"))).put("mtu", 1400)
        val route = config.getJSONObject("route")
        route.put("default_domain_resolver", "bootstrap")
        val previous = route.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray().put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        rules.put(JSONObject().put("ip_is_private", true).put("action", if (settings.optBoolean("allowLan", true)) "route" else "reject").apply {
            if (settings.optBoolean("allowLan", true)) put("outbound", "direct")
        })
        for (index in 0 until previous.length()) rules.put(previous.get(index))
        route.put("rules", rules)
        return config.toString()
    }
}
