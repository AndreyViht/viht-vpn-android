package com.pingwin.vpn

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class LocalAuthRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

object LocalAuthProtocol {
    private fun line(input: InputStream, limit: Int): String {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() < limit) {
            val value = input.read()
            if (value < 0) throw IllegalArgumentException("Incomplete HTTP request")
            if (value == 10) return buffer.toString("US-ASCII").removeSuffix("\r")
            buffer.write(value)
        }
        throw IllegalArgumentException("HTTP line too long")
    }
    fun read(input: InputStream): LocalAuthRequest {
        val first = line(input, 8192).split(' ')
        require(first.size == 3 && first[2] in setOf("HTTP/1.0", "HTTP/1.1")) { "Invalid HTTP request" }
        val target = URI(first[1])
        require(!target.isAbsolute && target.rawAuthority == null && target.rawFragment == null && target.path == "/auth/callback") { "Invalid callback path" }
        val headers = linkedMapOf<String, String>()
        var total = 0
        while (true) {
            val text = line(input, 8192)
            total += text.length
            require(total <= 16384) { "Headers too large" }
            if (text.isEmpty()) break
            val separator = text.indexOf(':')
            require(separator > 0) { "Invalid HTTP header" }
            val name = text.substring(0, separator).trim().lowercase(Locale.ROOT)
            require(!headers.containsKey(name)) { "Duplicate HTTP header" }
            headers[name] = text.substring(separator + 1).trim()
        }
        require(!headers.containsKey("transfer-encoding")) { "Chunked callbacks are not accepted" }
        val length = headers["content-length"]?.let { require(it.matches(Regex("[0-9]+"))) { "Invalid content length" }; it.toIntOrNull() ?: error("Invalid content length") } ?: 0
        require(length in 0..16384) { "Callback body too large" }
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            require(count > 0) { "Incomplete callback body" }
            offset += count
        }
        val query = target.rawQuery.orEmpty().split('&').filter(String::isNotBlank).map { item ->
            val pair = item.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        require(query.map { it.first }.distinct().size == query.size) { "Duplicate callback parameter" }
        return LocalAuthRequest(first[0], target.path, query.toMap(), headers, String(body, Charsets.UTF_8))
    }
    fun authorized(request: LocalAuthRequest, nonce: String): Boolean {
        return request.method in setOf("POST", "OPTIONS") && request.query["state"] == nonce &&
            request.headers["origin"] in setOf("https://anviht.ru", "https://www.anviht.ru")
    }
}
