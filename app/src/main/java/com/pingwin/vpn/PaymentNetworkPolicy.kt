package com.pingwin.vpn

import org.json.JSONArray
import org.json.JSONObject

/** Mandatory payment routing is independent of optional Russian-site bypass. */
object PaymentNetworkPolicy {
    val domains = listOf("yoomoney.ru", "yookassa.ru", "nspk.ru", "yastatic.net", "facct.ru", "smartcaptcha.yandexcloud.net")

    fun directPackages(ownPackage: String, settings: JSONObject): List<String> =
        (listOf(ownPackage) + VpnBypassSettings.packages(settings)).distinct()

    fun apply(config: JSONObject) {
        val dns = config.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        if ((0 until servers.length()).none { servers.getJSONObject(it).optString("tag") == "bootstrap" }) {
            servers.put(JSONObject().put("type","udp").put("tag","bootstrap").put("server","1.1.1.1"))
        }
        val suffixes = JSONArray(domains)
        fun paymentRule(rule: JSONObject) = rule.optJSONArray("domain_suffix")?.toString() == suffixes.toString()
        val dnsRules = JSONArray().put(JSONObject().put("domain_suffix",suffixes).put("server","bootstrap"))
        val oldDns = dns.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until oldDns.length()) if (!paymentRule(oldDns.getJSONObject(i))) dnsRules.put(oldDns.get(i))
        dns.put("rules",dnsRules).put("reverse_mapping",true)
        val route = config.getJSONObject("route")
        val oldRules = route.optJSONArray("rules") ?: JSONArray()
        val direct = JSONObject().put("domain_suffix",suffixes).put("action","route").put("outbound","direct")
        val rules = JSONArray(); var inserted = false
        for (i in 0 until oldRules.length()) {
            val rule = oldRules.getJSONObject(i)
            if (paymentRule(rule)) continue
            if (!inserted && rule.optString("action") !in setOf("sniff","hijack-dns")) { rules.put(direct); inserted = true }
            rules.put(rule)
        }
        if (!inserted) rules.put(direct)
        route.put("rules",rules)
    }
}
