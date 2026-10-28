package com.pingwin.vpn

import java.net.HttpURLConnection
import java.net.URL

data class HttpReply(val status: Int, val body: String)

object VihtHttp {
    fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null, readTimeoutMs: Int = 15_000): HttpReply {
        require(URL(url).protocol == "https") { "Only HTTPS service requests are allowed" }
        val connection = URL(url).openConnection() as HttpURLConnection
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
