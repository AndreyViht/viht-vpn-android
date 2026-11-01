package com.pingwin.vpn

import org.json.JSONArray
import org.json.JSONObject

/** Converts our saved configuration, never rewrites subscription credentials. */
internal object XrayConfigBuilder {
    fun supported(source: String): Boolean = runCatching {
        val outbound = proxy(JSONObject(source))
        outbound.getString("type") in setOf("vless", "vmess", "trojan", "shadowsocks") &&
            (outbound.optJSONObject("transport")?.optString("type") ?: "tcp") in setOf("tcp", "ws", "grpc", "httpupgrade")
    }.getOrDefault(false)

    private fun proxy(root: JSONObject): JSONObject {
        val outbounds = root.getJSONArray("outbounds")
        return (0 until outbounds.length()).map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "proxy-out" }
    }

    fun build(source: String, socksPort: Int, healthPort: Int, username: String, password: String): JSONObject {
        require(socksPort in 1..65535 && healthPort in 1..65535 && socksPort != healthPort)
        require(supported(source)) { "Этот протокол пока требует прежнее ядро VPN" }
        val root = JSONObject(source)
        val sourceProxy = proxy(root)
        val protocol = sourceProxy.getString("type")
        val user = JSONObject()
        val server = JSONObject().put("address", sourceProxy.getString("server")).put("port", sourceProxy.getInt("server_port"))
        val settings = when (protocol) {
            "vless", "vmess" -> {
                user.put("id", sourceProxy.getString("uuid"))
                if (protocol == "vless") {
                    user.put("encryption", "none")
                    sourceProxy.optString("flow").takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
                } else user.put("security", sourceProxy.optString("security", "auto")).put("alterId", sourceProxy.optInt("alter_id", 0))
                JSONObject().put("vnext", JSONArray().put(server.put("users", JSONArray().put(user))))
            }
            "trojan" -> JSONObject().put("servers", JSONArray().put(server.put("password", sourceProxy.getString("password"))))
            else -> JSONObject().put("servers", JSONArray().put(server.put("method", sourceProxy.getString("method")).put("password", sourceProxy.getString("password"))))
        }
        val outbound = JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings).put("streamSettings", stream(sourceProxy))
        val sniff = JSONObject().put("enabled", true).put("destOverride", JSONArray(listOf("http", "tls"))).put("routeOnly", true)
        val inbounds = JSONArray()
            .put(JSONObject().put("tag", "viht-tun").put("listen", "127.0.0.1").put("port", socksPort).put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "password").put("accounts", JSONArray().put(JSONObject().put("user", username).put("pass", password))).put("udp", true).put("ip", "127.0.0.1"))
                .put("sniffing", sniff))
            .put(JSONObject().put("tag", "viht-health").put("listen", "127.0.0.1").put("port", healthPort).put("protocol", "http").put("settings", JSONObject()))
        val rules = JSONArray()
            .put(rule("proxy").put("inboundTag", JSONArray(listOf("viht-health"))))
            .put(rule("proxy").put("inboundTag", JSONArray(listOf("viht-dns"))))
            .put(rule("dns").put("inboundTag", JSONArray(listOf("viht-tun"))).put("port", "53").put("network", "tcp,udp"))
        val oldRules = root.getJSONObject("route").optJSONArray("rules") ?: JSONArray()
        for (i in 0 until oldRules.length()) {
            val old = oldRules.getJSONObject(i)
            if (old.has("inbound") || old.optString("action") in setOf("sniff", "hijack-dns")) continue
            val target = if (old.optString("action") == "reject") "block" else old.optString("outbound")
            if (target !in setOf("direct", "proxy", "block")) continue
            val converted = rule(target)
            val domains = JSONArray()
            old.optJSONArray("domain")?.let { array -> for (j in 0 until array.length()) domains.put("full:" + array.getString(j)) }
            old.optJSONArray("domain_suffix")?.let { array -> for (j in 0 until array.length()) domains.put("domain:" + array.getString(j).trimStart('.')) }
            if (domains.length() > 0) converted.put("domain", domains)
            old.optJSONArray("ip_cidr")?.let { converted.put("ip", it) }
            if (old.optBoolean("ip_is_private")) converted.put("ip", JSONArray(listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16", "fc00::/7", "fe80::/10", "::1/128")))
            old.optJSONArray("port")?.let { converted.put("port", (0 until it.length()).joinToString(",") { j -> it.get(j).toString() }) }
            old.optString("network").takeIf { it.isNotBlank() }?.let { converted.put("network", it) }
            // Package exclusions are enforced by VpnService.Builder, not a process lookup per flow.
            if (converted.length() > 2) rules.put(converted)
        }
        val dns = root.getJSONObject("dns")
        val dnsServers = dns.getJSONArray("servers")
        val serversByTag = (0 until dnsServers.length()).map { dnsServers.getJSONObject(it) }.associateBy { it.optString("tag") }
        val remote = serversByTag["remote"]?.optString("server") ?: "1.1.1.1"
        val bootstrap = serversByTag["bootstrap"]?.optString("server") ?: "1.1.1.1"
        if (bootstrap != remote) {
            val reordered = JSONArray().put(rules.get(0))
                .put(rule("direct").put("inboundTag", JSONArray(listOf("viht-dns"))).put("ip", JSONArray(listOf(bootstrap))))
            for (i in 1 until rules.length()) reordered.put(rules.get(i))
            // Keep the health inbound first and the bootstrap-specific DNS rule before the DNS catch-all.
            for (i in 0 until reordered.length()) rules.put(i, reordered.get(i))
        }
        val xDnsServers = JSONArray()
        val dnsRules = dns.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until dnsRules.length()) {
            val old = dnsRules.getJSONObject(i)
            val suffixes = old.optJSONArray("domain_suffix") ?: continue
            if (old.optString("server") != "bootstrap") continue
            xDnsServers.put(JSONObject().put("address", bootstrap).put("domains", JSONArray((0 until suffixes.length()).map { "domain:" + suffixes.getString(it).trimStart('.') })).put("skipFallback", true))
        }
        xDnsServers.put(remote)
        return JSONObject().put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("tag", "viht-dns").put("queryStrategy", "UseIPv4").put("servers", xDnsServers))
            .put("inbounds", inbounds)
            .put("outbounds", JSONArray().put(outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
                .put(JSONObject().put("tag", "dns").put("protocol", "dns")))
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
    }

    private fun rule(outbound: String) = JSONObject().put("type", "field").put("outboundTag", outbound)

    private fun stream(outbound: JSONObject): JSONObject {
        val transport = outbound.optJSONObject("transport")
        val network = transport?.optString("type") ?: "tcp"
        val stream = JSONObject().put("network", network)
        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().put("path", transport!!.optString("path", "/")).put("headers", transport.optJSONObject("headers") ?: JSONObject()))
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", transport!!.optString("service_name")))
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().put("path", transport!!.optString("path", "/")).put("host", transport.optString("host")))
        }
        val tls = outbound.optJSONObject("tls")
        if (tls?.optBoolean("enabled") == true) {
            val reality = tls.optJSONObject("reality")
            val options = JSONObject().put("serverName", tls.optString("server_name"))
            tls.optJSONObject("utls")?.optString("fingerprint")?.takeIf { it.isNotBlank() }?.let { options.put("fingerprint", it) }
            if (reality?.optBoolean("enabled") == true) {
                options.put("password", reality.getString("public_key")).put("shortId", reality.optString("short_id"))
                stream.put("security", "reality").put("realitySettings", options)
            } else {
                options.put("allowInsecure", tls.optBoolean("insecure", false))
                tls.optJSONArray("alpn")?.let { options.put("alpn", it) }
                stream.put("security", "tls").put("tlsSettings", options)
            }
        } else stream.put("security", "none")
        return stream
    }
}
