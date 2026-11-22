package com.pingwin.vpn

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object VpnDiagnostics {
    private const val HEALTH_URL = "https://anviht.ru/download/viht-vpn-health.txt"
    private const val HEALTH_BODY = "viht-vpn-ok"
    private val healthTargets = listOf(
        HEALTH_URL,
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    fun probeTunnel(port: Int, timeoutMillis: Int = 4_000, onFailure: (String) -> Unit = {}): Boolean = healthTargets.any { target ->
        runCatching {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
            val connection = URL(target).openConnection(proxy) as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                if (target == HEALTH_URL) {
                    connection.responseCode == 200 &&
                        connection.inputStream.use { stream ->
                            val expected = (HEALTH_BODY + "\n").toByteArray(Charsets.US_ASCII)
                            expected.all { stream.read() == it.toInt() } && stream.read() == -1
                        }
                } else connection.responseCode == 204
            } finally {
                connection.disconnect()
            }
        }.onSuccess { success ->
            if (!success) onFailure("Tunnel probe ${URL(target).host}: unexpected HTTP response")
        }.onFailure { failure ->
            onFailure("Tunnel probe ${URL(target).host}: ${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(180)}")
        }.getOrDefault(false)
    }

    fun check(context: Context, server: JSONObject?): JSONObject {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val networks = PhysicalNetworks.ranked(manager)
        val host = server?.optString("host").orEmpty()
        val port = server?.optInt("port", 0) ?: 0
        val serverSelected = host.isNotBlank() && port in 1..65535
        var dnsWorks = false
        var serverReachable = false
        for (network in networks) {
            if (runCatching { network.getAllByName("anviht.ru").isNotEmpty() }.getOrDefault(false)) dnsWorks = true
            if (!serverSelected || serverReachable) continue
            val addresses = runCatching { network.getAllByName(host) }.getOrDefault(emptyArray())
            serverReachable = addresses.take(2).any { address ->
                runCatching {
                    network.socketFactory.createSocket().use { it.connect(InetSocketAddress(address, port), 1_200) }
                }.isSuccess
            }
        }
        val tunnelPort = VpnStatus.healthPort
        val connected = VpnStatus.state.value == VpnConnectionState.CONNECTED && tunnelPort > 0
        val tunnelWorks = connected && probeTunnel(tunnelPort) && VpnStatus.healthPort == tunnelPort
        return JSONObject().put("network", if (networks.isEmpty()) "failed" else "ok")
            .put("dns", if (networks.isEmpty()) "skipped" else if (dnsWorks) "ok" else "failed")
            .put("server", if (!serverSelected || networks.isEmpty()) "skipped" else if (serverReachable) "ok" else "failed")
            .put("tunnel", if (!connected) "skipped" else if (tunnelWorks) "ok" else "failed")
    }
}
