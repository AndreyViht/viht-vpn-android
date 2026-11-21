package com.pingwin.vpn

import org.json.JSONArray
import org.json.JSONObject

/** Beeline uses the verified Russian exit selected by the server routing policy. */
object BeelineNetworkPolicy {
    val domains = listOf("beeline.ru")
    fun apply(config: JSONObject) {
        val dns=config.getJSONObject("dns")
        val suffixes=JSONArray(domains)
        fun operatorRule(rule: JSONObject)=rule.optJSONArray("domain_suffix")?.toString()==suffixes.toString()
        val oldDns=dns.optJSONArray("rules") ?: JSONArray()
        val dnsRules=JSONArray().put(JSONObject().put("domain_suffix",suffixes).put("server","remote"))
        for(i in 0 until oldDns.length()) if(!operatorRule(oldDns.getJSONObject(i))) dnsRules.put(oldDns.get(i))
        dns.put("rules",dnsRules)
        val route=config.getJSONObject("route");val old=route.optJSONArray("rules") ?: JSONArray()
        val rules=JSONArray();var inserted=false
        val proxy=JSONObject().put("domain_suffix",suffixes).put("action","route").put("outbound","proxy")
        for(i in 0 until old.length()) {
            val r=old.getJSONObject(i)
            if(operatorRule(r)) continue
            if(!inserted && r.optString("action") !in setOf("sniff","hijack-dns")) {rules.put(proxy);inserted=true}
            rules.put(r)
        }
        if(!inserted)rules.put(proxy)
        route.put("rules",rules)
    }
}
