package com.pingwin.vpn

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom

object AndroidBrowserAuth {
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    suspend fun start(context: Context, provider: String): String {
        require(provider in setOf("yandex", "vk"))
        AndroidSessionStore.cancelPending()
        val pair = BrowserAuthCrypto.generate()
        val state = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        AndroidSessionStore.saveBrowserPending(context, JSONObject().put("state", state).put("provider", provider)
            .put("privateKey", encode(pair.private.encoded)).put("expires", System.currentTimeMillis() + 600_000))
        return Uri.parse("vihtvpn://auth/callback").buildUpon().appendQueryParameter("state", state)
            .appendQueryParameter("pk", encode(pair.public.encoded)).appendQueryParameter("app", context.packageName).build().toString()
    }
    suspend fun cancel(context: Context) { AndroidSessionStore.cancelPending(); AndroidSessionStore.saveBrowserPending(context, null) }
    suspend fun complete(context: Context, uri: Uri): Pair<String, String> {
        require(uri.scheme == "vihtvpn" && uri.host == "auth" && uri.path == "/callback" && uri.toString().length < 30000)
        val pending = AndroidSessionStore.loadBrowserPending(context) ?: error("Начните вход заново в приложении")
        val state = uri.getQueryParameter("state") ?: error("Нет подтверждения входа")
        require(state == pending.getString("state") && pending.getLong("expires") > System.currentTimeMillis()) { "Время входа истекло. Начните вход заново." }
        val json = JSONObject(BrowserAuthCrypto.decrypt(decode(pending.getString("privateKey")),
            decode(uri.getQueryParameter("key") ?: error("Нет ключа входа")),
            decode(uri.getQueryParameter("iv") ?: error("Нет подтверждения входа")),
            decode(uri.getQueryParameter("payload") ?: error("Нет данных входа")), state))
        val provider = pending.getString("provider")
        val token = AndroidSessionStore.accept(context, json.getString("token"), json.optString("refresh_token"), provider)
        AndroidSessionStore.saveBrowserPending(context, null)
        return token to provider
    }
}
