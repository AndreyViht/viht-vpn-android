package com.pingwin.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

class AndroidVihtBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingConnectCallbackId: String? = null
    private var pendingConfigData: Pair<String, String>? = null

    private fun respond(callbackId: String, json: Any) {
        val jsonStr = when (json) {
            is JSONObject -> json.toString()
            is JSONArray -> json.toString()
            is String -> JSONObject.quote(json)
            else -> json.toString()
        }
        activity.runOnUiThread {
            webView.evaluateJavascript("window.__vihtNativeCallback('$callbackId', $jsonStr);", null)
        }
    }

    @JavascriptInterface
    fun minimizeWindow() {
        activity.runOnUiThread {
            activity.moveTaskToBack(true)
        }
    }

    @JavascriptInterface
    fun closeWindow() {
        activity.runOnUiThread {
            activity.moveTaskToBack(true)
        }
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        if (url.isBlank()) return
        activity.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                activity.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(activity, "Не удалось открыть: $url", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun getSystemInfo(callbackId: String) {
        val hwid = VihtPreferences.getHwid(activity)
        val info = JSONObject().apply {
            put("hwid", hwid)
            put("platform", "android")
            put("hostname", Build.MODEL)
            put("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64")
        }
        respond(callbackId, info)
    }

    @JavascriptInterface
    fun getAppVersion(callbackId: String) {
        respond(callbackId, "1.3.3")
    }

    @JavascriptInterface
    fun getVpnStatus(callbackId: String) {
        val state = when (VpnStatus.state.value) {
            VpnConnectionState.CONNECTED -> "connected"
            VpnConnectionState.CONNECTING -> "connecting"
            VpnConnectionState.DISCONNECTED -> "disconnected"
            VpnConnectionState.ERROR -> "error"
        }
        val status = JSONObject().apply {
            put("ok", true)
            put("state", state)
        }
        respond(callbackId, status)
    }

    @JavascriptInterface
    fun vpnConnect(callbackId: String, serverJson: String, settingsJson: String) {
        scope.launch {
            try {
                val srvObj = JSONObject(serverJson)
                val setObj = if (settingsJson.isNotBlank()) JSONObject(settingsJson) else JSONObject()

                val serverId = srvObj.optString("id", "de_1")
                val vlessUri = srvObj.optString("vlessUri", "")

                if (vlessUri.isBlank()) {
                    respond(callbackId, JSONObject().apply {
                        put("ok", false)
                        put("error", "VLESS ключ не найден")
                    })
                    return@launch
                }

                val bypassRu = setObj.optBoolean("bypassRussianSites", true)

                val profile = ConnectionProfileParser.parse(vlessUri)
                val routing = RoutingSettingsStore.load(activity).copy(
                    bypassRussiaSites = bypassRu
                )

                val config = ConnectionConfigBuilder.build(
                    profile = profile,
                    routing = routing,
                    detailedLogging = false
                )

                val validation = LibboxValidator.validate(config)
                if (validation.isFailure) {
                    respond(callbackId, JSONObject().apply {
                        put("ok", false)
                        put("error", "Неверная конфигурация VLESS")
                    })
                    return@launch
                }

                activity.runOnUiThread {
                    val permissionIntent = VpnService.prepare(activity)
                    if (permissionIntent != null) {
                        pendingConnectCallbackId = callbackId
                        pendingConfigData = Pair(config, serverId)
                        activity.launchVpnPermission(permissionIntent)
                    } else {
                        AutoVlessVpnService.start(activity, config, serverId)
                        respond(callbackId, JSONObject().apply { put("ok", true) })
                    }
                }
            } catch (e: Exception) {
                respond(callbackId, JSONObject().apply {
                    put("ok", false)
                    put("error", e.message ?: "Ошибка подключения")
                })
            }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val callbackId = pendingConnectCallbackId
        val configData = pendingConfigData
        pendingConnectCallbackId = null
        pendingConfigData = null

        if (granted && configData != null) {
            AutoVlessVpnService.start(activity, configData.first, configData.second)
            if (callbackId != null) {
                respond(callbackId, JSONObject().apply { put("ok", true) })
            }
        } else {
            VpnStatus.set(VpnConnectionState.ERROR)
            if (callbackId != null) {
                respond(callbackId, JSONObject().apply {
                    put("ok", false)
                    put("error", "Разрешение на VPN не получено")
                })
            }
        }
    }

    @JavascriptInterface
    fun vpnDisconnect(callbackId: String) {
        activity.runOnUiThread {
            AutoVlessVpnService.stop(activity)
            respond(callbackId, JSONObject().apply { put("ok", true) })
        }
    }

    @JavascriptInterface
    fun vpnUpdateSettings(callbackId: String, settingsJson: String) {
        respond(callbackId, JSONObject().apply { put("ok", true) })
    }

    @JavascriptInterface
    fun startAuthServer(callbackId: String, provider: String) {
        AuthCallbackServer.start { token, subToken, tgId, email ->
            val payload = JSONObject().apply {
                put("token", if (subToken.isNotBlank()) subToken else token)
                put("originalToken", token)
                put("subToken", subToken)
                put("tgId", tgId)
                put("email", email)
                put("provider", provider)
                put("daysLeft", 493)
            }
            activity.runOnUiThread {
                webView.evaluateJavascript("if (window.__onAuthSuccess) window.__onAuthSuccess($payload);", null)
            }
        }

        val res = JSONObject().apply {
            put("port", AuthCallbackServer.PORT)
            put("callbackUrl", "http://127.0.0.1:${AuthCallbackServer.PORT}/auth/callback")
        }
        respond(callbackId, res)
    }

    @JavascriptInterface
    fun pingServers(callbackId: String, serversJson: String) {
        scope.launch {
            try {
                val results = JSONObject()
                val arr = JSONArray(serversJson)
                val deferreds = (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = s.optString("id")
                    var host = s.optString("host", "anviht.ru")
                    var port = s.optInt("port", 443)
                    val vless = s.optString("vlessUri", "")
                    if (vless.isNotBlank()) {
                        val hostRegex = "@([^:?#/]+)(?::(\\d+))?".toRegex()
                        hostRegex.find(vless)?.let { m ->
                            host = m.groupValues[1]
                            m.groupValues[2].toIntOrNull()?.let { port = it }
                        }
                    }
                    async {
                        val ping = measureTcpPing(host, port)
                        Pair(id, ping)
                    }
                }
                deferreds.awaitAll().forEach { (id, ping) ->
                    results.put(id, ping)
                }
                respond(callbackId, results)
            } catch (e: Exception) {
                respond(callbackId, JSONObject())
            }
        }
    }

    private fun measureTcpPing(host: String, port: Int, timeoutMs: Int = 1500): Int {
        return runCatching {
            val start = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            (System.currentTimeMillis() - start).toInt()
        }.getOrDefault(50)
    }

    @JavascriptInterface
    fun saveCredentials(callbackId: String, key: String, value: String) {
        val prefs = activity.getSharedPreferences("viht_credentials", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
        respond(callbackId, JSONObject().apply { put("ok", true) })
    }

    @JavascriptInterface
    fun getCredentials(callbackId: String, key: String) {
        val prefs = activity.getSharedPreferences("viht_credentials", Context.MODE_PRIVATE)
        val v = prefs.getString(key, null)
        respond(callbackId, v ?: "")
    }

    @JavascriptInterface
    fun clearCredentials(callbackId: String) {
        val prefs = activity.getSharedPreferences("viht_credentials", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        respond(callbackId, JSONObject().apply { put("ok", true) })
    }
}
