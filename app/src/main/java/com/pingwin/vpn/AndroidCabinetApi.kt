package com.pingwin.vpn

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException

/** Fixed service endpoint: WebView cannot turn this into a general HTTP proxy. */
object AndroidCabinetApi {
    private val readActions = setOf("profile", "active-devices", "payments")
    private val writeActions = setOf("device-action", "reset-keys")
    suspend fun request(context: Context, options: JSONObject): JSONObject {
        return try {
            val action = options.getString("action")
            val method = options.optString("method", "GET")
            require((method == "GET" && action in readActions) || (method == "POST" && action in writeActions)) { "Недопустимое действие кабинета" }
            val url = Uri.parse("https://anviht.ru/api/cabinet-api").buildUpon()
                .appendQueryParameter("action", action)
                .appendQueryParameter("hwid", VihtPreferences.getHwid(context))
                .appendQueryParameter("app_version", BuildConfig.VERSION_NAME)
                .appendQueryParameter("ts", System.currentTimeMillis().toString()).build().toString()
            var access = AndroidSessionStore.getToken(context)
            val body = if (method == "POST") options.optJSONObject("body")?.toString() ?: "{}" else null
            suspend fun send(): HttpReply {
                val headers = mapOf("Authorization" to "Bearer $access", "Accept" to "application/json", "User-Agent" to "VihtVPN-Android/" + BuildConfig.VERSION_NAME)
                return try { VihtHttp.request(url, method, headers, body) }
                catch (failure: IOException) {
                    if (method != "GET") throw failure
                    delay(400)
                    VihtHttp.request(url, method, headers, body)
                }
            }
            var reply = send()
            if (reply.status == 401) {
                access = AndroidSessionStore.getToken(context, access)
                reply = send()
            }
            if (reply.status == 401) throw SessionFailure("Сессия истекла. Войдите заново.", true)
            var data = runCatching { JSONObject(reply.body) }.getOrNull()
            if (action == "profile" && reply.status in 200..299 && data?.optBoolean("linked", true) == false) {
                val provisionUrl = Uri.parse(url).buildUpon().clearQuery().appendQueryParameter("action", "skip-link").build().toString()
                val provision = VihtHttp.request(provisionUrl, "POST", mapOf("Authorization" to "Bearer $access"), "{}")
                if (provision.status !in 200..299) return JSONObject().put("ok", false).put("error", "Не удалось создать профиль. Повторите вход.")
                reply = send()
                data = runCatching { JSONObject(reply.body) }.getOrNull()
            }
            if (reply.status !in 200..299 || data == null) {
                JSONObject().put("ok", false).put("error", data?.optString("error")?.takeIf(String::isNotBlank) ?: "Ошибка сервиса: HTTP ${reply.status}")
            } else JSONObject().put("ok", data.optBoolean("ok", true)).put("data", data).put("error", data.opt("error") ?: JSONObject.NULL)
        } catch (failure: SessionFailure) {
            JSONObject().put("ok", false).put("unauthorized", failure.unauthorized).put("error", failure.message)
        } catch (_: IOException) {
            JSONObject().put("ok", false).put("error", "Не удалось связаться с сервисом. Проверьте интернет и нажмите «Повторить».")
        }
    }
}
