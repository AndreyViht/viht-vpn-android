package com.pingwin.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong

class AndroidVihtBridge(private val activity: MainActivity, private val webView: WebView) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operation = AtomicLong(0)
    private val connectLock = Mutex()
    private var permission: CompletableDeferred<Boolean>? = null
    private var connectingJob: Job? = null
    private val updates = AndroidUpdateBridge(activity) { data -> emit("update-progress", data) }
    @Volatile private var closed = false
    private val preferences get() = activity.getSharedPreferences("viht_ui_settings", Context.MODE_PRIVATE)

    private fun respond(id: String, data: Any) {
        val encoded = when(data) { is JSONObject, is JSONArray -> data.toString(); is String -> JSONObject.quote(data); else -> data.toString() }
        activity.runOnUiThread {
            if (!closed && activity.isTrustedPage) webView.evaluateJavascript("window.__vihtNativeCallback?.(" + JSONObject.quote(id) + "," + encoded + ");", null)
        }
    }
    fun emit(type: String, data: JSONObject) {
        activity.runOnUiThread {
            if (!closed && activity.isTrustedPage) webView.evaluateJavascript("window.__vihtNativeEvent?.(" + JSONObject.quote(type) + "," + data.toString() + ");", null)
        }
    }
    private fun ok() = JSONObject().put("ok", true)
    private fun error(message: String) = JSONObject().put("ok", false).put("error", message)

    @JavascriptInterface fun request(id: String, method: String, arguments: String) {
        if (closed || !activity.isTrustedPage || id.length > 160 || arguments.length > 256_000) return
        scope.launch {
            try {
                val args = JSONArray(arguments)
                val result: Any = when(method) {
                    "getSystemInfo" -> JSONObject().put("platform", "android").put("deviceModel", VihtPreferences.getDeviceModel()).put("hwid", VihtPreferences.getHwid(activity)).put("manualConnectOnly", false)
                    "getAppVersion" -> BuildConfig.VERSION_NAME
                    "getUiSnapshot" -> ok().put("snapshot",AndroidSessionStore.uiSnapshot(activity) ?: JSONObject.NULL).put("vpn",VpnStatus.snapshot())
                    "cacheUiSnapshot" -> { AndroidSessionStore.cacheUi(activity,args.getJSONObject(0)); ok() }
                    "getInstalledApps" -> {
                        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        val apps=activity.packageManager.queryIntentActivities(intent,0).map { info -> info.activityInfo.packageName to info.loadLabel(activity.packageManager).toString() }.distinctBy { it.first }.filter { it.first!=activity.packageName }.sortedBy { it.second.lowercase() }
                        JSONArray().apply { apps.forEach { (pkg,label) -> put(JSONObject().put("packageName",pkg).put("label",label)) } }
                    }
                    "getVpnNotificationSettings" -> ok().put("enabled",VpnNotification.enabled(activity)).put("tileAdded",preferences.getBoolean("tileAdded",false))
                    "openVpnNotificationSettings" -> { val manager=activity.getSystemService(android.app.NotificationManager::class.java); val intent=Intent(if(manager.areNotificationsEnabled()) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,VpnNotification.CHANNEL); openIntent(intent); ok() }
                    "getVpnStatus" -> VpnStatus.snapshot()
                    "getOwnerSession" -> {
                        val options = args.optJSONObject(0) ?: JSONObject()
                        try { ok().put("token", AndroidSessionStore.getToken(activity, options.optString("rejectedToken"))) }
                        catch (failure: SessionFailure) { error(failure.message ?: "Войдите заново").put("unauthorized", failure.unauthorized) }
                    }
                    "clearOwnerSession" -> { AuthCallbackServer.stop(); AndroidSessionStore.clear(activity); ok() }
                    "startAuthServer" -> {
                        val provider = args.getString(0)
                        AuthCallbackServer.stop()
                        val callback = AndroidBrowserAuth.start(activity.applicationContext, provider)
                        preferences.edit().putString("provider", provider).apply()
                        JSONObject().put("callbackUrl", callback)
                    }
                    "cancelAuthServer" -> { AuthCallbackServer.stop(); AndroidBrowserAuth.cancel(activity); ok() }
                    "cabinetRequest" -> AndroidCabinetApi.request(activity, args.getJSONObject(0))
                    "fetchSubscription" -> {
                        val token = args.getString(0)
                        require(token.isNotBlank() && token.length < 512) { "Не найдена подписка" }
                        val url = "https://anviht.ru/api/subscription-link?format=json&token=" + URLEncoder.encode(token, "UTF-8")
                        val reply = VihtHttp.request(url, headers = mapOf("Accept" to "application/json, text/plain", "User-Agent" to "VihtVPN-Android/" + BuildConfig.VERSION_NAME,
                            "x-hwid" to VihtPreferences.getHwid(activity), "x-device-model" to VihtPreferences.getDeviceModel(), "x-device-os" to "Android", "x-app-name" to "Viht Android", "x-app-version" to BuildConfig.VERSION_NAME))
                        if (reply.status in 200..299) ok().put("body", reply.body) else error("Не удалось загрузить подписку: HTTP " + reply.status)
                    }
                    "syncServers" -> { syncServers(args.getJSONArray(0)); ok() }
                    "vpnConnect" -> connect(args.getJSONObject(0), args.optJSONObject(1) ?: JSONObject())
                    "vpnDisconnect" -> { disconnect(); ok() }
                    "vpnUpdateSettings" -> applySettings(args.getJSONObject(0))
                    "pingServers" -> ping(args.getJSONArray(0))
                    "exportDebugLog" -> DiagnosticLogStore.entries(activity).joinToString("\n")
                    "setDesktopPreferences" -> {
                        val value = args.getJSONObject(0)
                        if (value.has("notifyUpdate")) { preferences.edit().putBoolean("notifyUpdate", value.getBoolean("notifyUpdate")).apply(); UpdateSettingsStore.setAutoCheckEnabled(activity, value.getBoolean("notifyUpdate")); UpdateScheduler.sync(activity) }
                        ok()
                    }
                    "getDesktopPreferences" -> JSONObject().put("notifyUpdate", preferences.getBoolean("notifyUpdate", true))
                    "getLoginItemSettings" -> JSONObject().put("openAtLogin", false).put("startMinimized", false)
                    "setLoginItemSettings" -> error("Автозапуск Windows недоступен на Android")
                    "copyText" -> { withContext(Dispatchers.Main) {
                        val value=args.getString(0); require(value.length <= 8192)
                        val clip=android.content.ClipData.newPlainText("Viht VPN",value)
                        if (Build.VERSION.SDK_INT >= 33) clip.description.extras=android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true) }
                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                    }; ok() }
                    "addQuickTile" -> withContext(Dispatchers.Main) { activity.addQuickTile() }
                    "openBatterySettings" -> { openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName))); ok() }
                    "openVpnSettings" -> { openIntent(Intent(Settings.ACTION_VPN_SETTINGS)); ok() }
                    "openNotificationSettings" -> { openIntent(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)); ok() }
                    "openPayment" -> {
                        val url = args.getString(0)
                        require(PaymentActivity.isPaymentEntry(url)) { "Недопустимая платёжная ссылка" }
                        openIntent(Intent(activity, PaymentActivity::class.java).putExtra("paymentUrl", url)); ok()
                    }
                    "openExternal" -> {
                        val url = Uri.parse(args.getString(0)); require(url.scheme in setOf("https", "http")) { "Недопустимая ссылка" }
                        openIntent(Intent(Intent.ACTION_VIEW, url)); ok()
                    }
                    "checkForUpdates" -> updates.check()
                    "getUpdateStatus" -> updates.status()
                    "downloadUpdate" -> updates.download(args.getJSONObject(0))
                    "applyUpdateAndRestart" -> updates.install(args.getJSONObject(0))
                    else -> error("Недоступный метод Android: " + method)
                }
                respond(id, result)
            } catch (_: CancellationException) { if (!closed) respond(id, error("Операция отменена").put("cancelled", true)) }
            catch (failure: Exception) { respond(id, error(failure.message ?: "Не удалось выполнить действие")) }
        }
    }
    private suspend fun openIntent(intent: Intent) = withContext(Dispatchers.Main) { activity.startActivity(intent) }
    fun onVpnPermissionResult(granted: Boolean) { permission?.complete(granted); permission = null }
    private suspend fun connect(server: JSONObject, settings: JSONObject): JSONObject {
        if (!connectLock.tryLock()) return error("Подключение уже выполняется")
        val epoch = operation.incrementAndGet()
        connectingJob = currentCoroutineContext()[Job]
        var serviceDispatched = false
        try {
            if (VpnStatus.state.value == VpnConnectionState.CONNECTED || VpnStatus.state.value == VpnConnectionState.CONNECTING) return error("Сначала отключите текущее VPN-подключение")
            val link = server.optString("vless_uri").ifBlank { server.optString("vlessUri") }
            require(link.isNotBlank()) { "Нет актуального ключа. Синхронизируйте подписку." }
            val profile = ConnectionProfileParser.parse(link)
            val config = VihtVpnConfig.build(profile, settings)
            LibboxValidator.validate(config).getOrThrow()
            val intent = VpnService.prepare(activity)
            if (intent != null) {
                val waiting = CompletableDeferred<Boolean>(); permission = waiting
                withContext(Dispatchers.Main) { activity.launchVpnPermission(intent) }
                if (!withTimeout(60_000) { waiting.await() }) {
                    VpnStatus.fail("Разрешение на VPN не получено", false)
                    return error("Разрешение на VPN не получено")
                }
            }
            if (epoch != operation.get()) return error("Подключение отменено").put("cancelled", true)
            val id = server.getString("id")
            val saved = ConnectionStore.loadAll(activity).filterNot { it.id == id } + SavedConnection(id, server.optString("name", "Viht VPN"), link)
            ConnectionStore.saveAll(activity, saved); ConnectionStore.select(activity, id)
            preferences.edit().putString("vpnSettings", settings.toString()).apply()
            activity.ensureNotificationPermission()
            VpnStatus.set(VpnConnectionState.CONNECTING)
            AutoVlessVpnService.start(activity, config, id)
            serviceDispatched = true
            val state = withTimeout(30_000) { VpnStatus.state.first { (it == VpnConnectionState.CONNECTED && VpnStatus.activeConnectionId.value == id) || it == VpnConnectionState.ERROR } }
            return if (state == VpnConnectionState.CONNECTED) ok() else error(VpnStatus.lastError ?: "Не удалось подключиться")
        } catch (failure: TimeoutCancellationException) {
            AutoVlessVpnService.stop(activity)
            VpnStatus.fail("Время подключения истекло. Повторите попытку.", false)
            return error("Время подключения истекло. Повторите попытку.")
        } catch (failure: CancellationException) {
            if (!serviceDispatched && epoch == operation.get()) { AutoVlessVpnService.stop(activity); VpnStatus.set(VpnConnectionState.DISCONNECTED) }
            throw failure
        } catch (failure: Exception) {
            AutoVlessVpnService.stop(activity)
            VpnStatus.fail(failure.message ?: "Не удалось запустить VPN", false)
            return error(VpnStatus.lastError ?: "Не удалось запустить VPN")
        } finally { permission = null; connectingJob = null; connectLock.unlock() }
    }
    private suspend fun disconnect() {
        VpnStatus.markManualStop()
        operation.incrementAndGet(); permission?.complete(false); permission = null
        connectingJob?.takeIf { it != currentCoroutineContext()[Job] }?.cancel()
        AutoVlessVpnService.stop(activity)
        withTimeout(15_000) { VpnStatus.state.first { it == VpnConnectionState.DISCONNECTED || it == VpnConnectionState.ERROR } }
    }
    private suspend fun applySettings(settings: JSONObject): JSONObject {
        VpnBypassSettings.domains(settings); VpnBypassSettings.packages(settings)
        val previous = JSONObject(preferences.getString("vpnSettings", "{}") ?: "{}")
        val changed = listOf("bypassRussia", "dnsAdblock", "allowLan").any { previous.optBoolean(it, true) != settings.optBoolean(it, true) } || VpnBypassSettings.domains(previous)!=VpnBypassSettings.domains(settings) || VpnBypassSettings.packages(previous)!=VpnBypassSettings.packages(settings)
        val state=VpnStatus.state.value
        if(changed && state==VpnConnectionState.CONNECTING) return error("Дождитесь завершения подключения")
        if(!changed || state in setOf(VpnConnectionState.DISCONNECTED,VpnConnectionState.ERROR)) {
            preferences.edit().putString("vpnSettings",settings.toString()).apply();return ok()
        }
        val selected=ConnectionStore.selected(activity) ?: return error("Не найден текущий ключ")
        val server=JSONObject().put("id",selected.id).put("name",selected.name).put("vless_uri",selected.link)
        disconnect()
        val result=connect(server,settings)
        if(!result.optBoolean("ok")) {
            preferences.edit().putString("vpnSettings",previous.toString()).apply()
            runCatching { connect(server,previous) }
        }
        return result
    }
    private fun syncServers(array: JSONArray) {
        val saved = (0 until array.length()).mapNotNull { index ->
            val server = array.optJSONObject(index) ?: return@mapNotNull null
            val link = server.optString("vless_uri").ifBlank { server.optString("vlessUri") }
            if (link.isBlank()) null else SavedConnection(server.getString("id"), server.optString("name", "Viht VPN"), link)
        }
        ConnectionStore.saveAll(activity, saved)
    }
    private suspend fun ping(array: JSONArray): JSONObject = coroutineScope {
        val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = manager.allNetworks.firstOrNull { candidate ->
            manager.getNetworkCapabilities(candidate)?.let { it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) } == true
        }
        val values = (0 until minOf(array.length(), 32)).map { index -> async {
            val server = array.getJSONObject(index)
            val value = runCatching {
                require(network != null) { "Нет физического подключения" }
                val uri = java.net.URI(server.getString("vless_uri"))
                val host = server.optString("host").ifBlank { uri.host }
                val port = server.optInt("port", if (uri.port < 0) 443 else uri.port)
                val addresses = network.getAllByName(host)
                val address = addresses.firstOrNull { it is java.net.Inet4Address } ?: addresses.first()
                (1..2).mapNotNull {
                    runCatching {
                        val start = System.nanoTime()
                        network.socketFactory.createSocket().use { socket -> socket.connect(InetSocketAddress(address, port), 2500) }
                        maxOf(1, ((System.nanoTime() - start) / 1_000_000).toInt())
                    }.getOrNull()
                }.minOrNull()
            }.getOrNull()
            server.getString("id") to value
        } }.awaitAll()
        JSONObject().apply { values.forEach { (id, value) -> put(id, value ?: JSONObject.NULL) } }
    }
    fun completeBrowserAuth(uri: Uri?) {
        if (uri?.scheme != "vihtvpn" || uri.host != "auth" || uri.path != "/callback") return
        scope.launch {
            try {
                val (token, provider) = AndroidBrowserAuth.complete(activity.applicationContext, uri)
                emit("auth-success", JSONObject().put("token", token).put("originalToken", token).put("provider", provider))
            } catch (_: CancellationException) { }
            catch (failure: Exception) { emit("auth-error", error(failure.message ?: "Не удалось завершить вход. Начните вход заново.")) }
        }
    }
    fun requestQuickConnect() { emit("quick-connect", JSONObject()) }
    private var lastUpdateCheck = 0L
    fun pageReady() {
        if (activity.consumeTileConnect()) requestQuickConnect()
        if (activity.consumeOpenUpdates()) emit("open-updates", JSONObject())
        if (preferences.getBoolean("notifyUpdate", true) && System.currentTimeMillis() - lastUpdateCheck > 3_600_000) {
            lastUpdateCheck = System.currentTimeMillis()
            scope.launch { runCatching { updates.check() }.getOrNull()?.let { if (it.optBoolean("hasUpdate")) emit("update-available", it.getJSONObject("releaseInfo")) } }
        }
        emit("app-resumed",JSONObject())
        emit("vpn-state", VpnStatus.snapshot().put("snapshot",true))
        scope.launch {
            runCatching { AndroidSessionStore.getToken(activity) }.getOrNull()?.let { token -> emit("auth-success", JSONObject().put("token", token).put("originalToken", token).put("provider", preferences.getString("provider", "yandex")).put("restore", true)) }
        }
    }
    fun close() { closed = true; permission?.complete(false); scope.cancel() }
}
