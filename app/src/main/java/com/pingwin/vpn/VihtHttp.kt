package com.pingwin.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class HttpReply(val status: Int, val body: String)

object VihtHttp {
    @Volatile private var connectivity: ConnectivityManager? = null

    fun initialize(context: Context) {
        connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null, readTimeoutMs: Int = ServiceRequestPolicy.DEFAULT_READ_TIMEOUT_MS): HttpReply {
        val endpoint = URL(url)
        require(endpoint.protocol == "https") { "Only HTTPS service requests are allowed" }
        val manager = connectivity
        if (!endpoint.host.equals("anviht.ru", ignoreCase = true) || manager == null) {
            return send(endpoint, method, headers, body, readTimeoutMs, null)
        }
        // Bind this request only, not the process. A stalled VPN cannot capture its own
        // subscription, authentication, device list or recovery requests.
        return ServiceRequestPolicy.execute(method, readTimeoutMs,
            networks = { PhysicalNetworks.ranked(manager) }, clock = SystemClock::elapsedRealtime,
        ) { network, connectTimeout, readTimeout, deadline ->
            send(endpoint, method, headers, body, readTimeout, network, connectTimeout, deadline)
        }
    }

    private fun send(url: URL, method: String, headers: Map<String, String>, body: String?, readTimeoutMs: Int, network: Network?, connectTimeoutMs: Int = 10_000, deadline: Long? = null): HttpReply {
        require(url.protocol == "https") { "Only HTTPS service requests are allowed" }
        val connection = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs.coerceIn(1000, 120000)
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (deadline != null) {
                        val remaining = deadline - SystemClock.elapsedRealtime()
                        if (remaining <= 0) throw SocketTimeoutException("Service response deadline exceeded")
                        connection.readTimeout = minOf(readTimeoutMs.toLong(), remaining).toInt().coerceAtLeast(1)
                    }
                    val count = it.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 4 * 1024 * 1024) { "Ответ сервиса слишком большой" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }.orEmpty()
            HttpReply(status, response)
        } finally { connection.disconnect() }
    }
}
