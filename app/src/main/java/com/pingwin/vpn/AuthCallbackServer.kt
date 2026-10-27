package com.pingwin.vpn

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

object AuthCallbackServer {
    private var server: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var expiry: Job? = null

    @Synchronized fun stop() {
        server?.let { AndroidSessionStore.cancelPending() }
        runCatching { server?.close() }
        server = null
        expiry?.cancel(); expiry = null
        scope?.cancel(); scope = null
    }
    @Synchronized fun start(context: Context, provider: String, onSuccess: (String) -> Unit, onError: (String) -> Unit): String {
        require(provider in setOf("yandex", "vk")) { "Неподдерживаемый способ входа" }
        stop()
        val nonce = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = socket
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = worker
        var consumed = false
        fun reply(client: Socket, status: Int, origin: String?, data: JSONObject = JSONObject()) {
            val body = data.toString().toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 ").append(status).append(if (status == 200) " OK" else if (status == 204) " No Content" else " Error").append("\r\n")
                append("Content-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\n")
                if (origin in setOf("https://anviht.ru", "https://www.anviht.ru")) append("Access-Control-Allow-Origin: ").append(origin).append("\r\n")
                append("Access-Control-Allow-Private-Network: true\r\nAccess-Control-Allow-Methods: POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\n")
                append("Connection: close\r\nContent-Length: ").append(if (status == 204) 0 else body.size).append("\r\n\r\n")
            }
            client.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII)); if (status != 204) write(body); flush() }
        }
        expiry = worker.launch {
            kotlinx.coroutines.delay(300_000)
            if (server === socket) { onError("Время входа истекло. Повторите попытку."); stop() }
        }
        worker.launch {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    client.soTimeout = 10_000
                    var origin: String? = null
                    try {
                        val request = LocalAuthProtocol.read(client.getInputStream())
                        origin = request.headers["origin"]
                        if (!LocalAuthProtocol.authorized(request, nonce)) { reply(client, 403, origin); return@use }
                        if (request.method == "OPTIONS") { reply(client, 204, origin); return@use }
                        if (consumed || server !== socket) { reply(client, 409, origin); return@use }
                        val data = JSONObject(request.body)
                        consumed = true
                        val token = AndroidSessionStore.accept(context.applicationContext, data.getString("token"), data.optString("refresh_token").takeUnless { value -> value.isBlank() || value == "null" }, provider)
                        if (server !== socket) { reply(client, 409, origin); return@use }
                        reply(client, 200, origin, JSONObject().put("ok", true))
                        onSuccess(token)
                        runCatching { socket.close() }
                        if (server === socket) { server = null; expiry?.cancel() }
                    } catch (error: Exception) {
                        consumed = false
                        runCatching { reply(client, 400, origin, JSONObject().put("ok", false).put("error", "Не удалось завершить вход")) }
                        if (server === socket) onError(error.message ?: "Не удалось завершить вход")
                    }
                }
            }
        }
        return "http://127.0.0.1:" + socket.localPort + "/auth/callback?state=" + nonce
    }
}
