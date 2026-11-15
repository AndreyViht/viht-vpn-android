package com.pingwin.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.net.UnknownHostException
import java.net.HttpURLConnection
import java.net.URL

data class HttpReply(val status: Int, val body: String)

object VihtHttp {
    @Volatile private var connectivity: ConnectivityManager? = null

    fun initialize(context: Context) {
        connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null, readTimeoutMs: Int = 15_000): HttpReply {
        val endpoint = URL(url)
        try {
            return send(endpoint, method, headers, body, readTimeoutMs, null)
        } catch (failure: UnknownHostException) {
            if (!endpoint.host.equals("anviht.ru", ignoreCase = true)) throw failure
            val manager = connectivity ?: throw failure
            for (network in PhysicalNetworks.ranked(manager)) {
                try {
                    return send(endpoint, method, headers, body, readTimeoutMs, network)
                } catch (_: UnknownHostException) { /* Try the next physical network. */ }
            }
            throw UnknownHostException("Не удалось найти адрес anviht.ru. Проверьте подключение к интернету.").apply { initCause(failure) }
        }
    }

    private fun send(url: URL, method: String, headers: Map<String, String>, body: String?, readTimeoutMs: Int, network: Network?): HttpReply {
        require(url.protocol == "https") { "Only HTTPS service requests are allowed" }
        val connection = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
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
