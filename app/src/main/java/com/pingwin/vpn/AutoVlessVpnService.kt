package com.pingwin.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.annotation.RequiresApi
import java.io.File
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Proxy
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class AutoVlessVpnService :
    VpnService() {

    companion object {
        private const val TAG = "VihtVPN"
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_CONNECTION_ID = "connection_id"

        private const val ACTION_START = "START"
        private const val ACTION_STOP = "STOP"
        private const val ACTION_TILE = "QUICK_CONNECT"

        private const val NOTIFICATION_CHANNEL_ID = "autovless_vpn"
        private const val NOTIFICATION_ID = 1001

        fun start(
            context: Context,
            config: String,
            connectionId: String
        ) {
            val intent =
                Intent(
                    context,
                    AutoVlessVpnService::class.java
                ).apply {
                    action = ACTION_START
                    putExtra(EXTRA_CONFIG, config)
                    putExtra(EXTRA_CONNECTION_ID, connectionId)
                }

            context.startForegroundService(intent)
        }

        fun startFromTile(context: Context) {
            val intent=Intent(context, AutoVlessVpnService::class.java).setAction(ACTION_TILE)
            android.app.PendingIntent.getForegroundService(context,23,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE).send()
        }
        fun resumeAfterUpdate(context: Context) {
            if (VpnRunStore.desired(context) && VpnService.prepare(context) == null) {
                context.startForegroundService(Intent(context, AutoVlessVpnService::class.java).setAction("RESTORE"))
            }
        }
        fun stop(context: Context, reason: String? = null) {
            VpnRunStore.stop(context)
            VpnStatus.markManualStop()
            val intent =
                Intent(
                    context,
                    AutoVlessVpnService::class.java
                ).apply {
                    action = ACTION_STOP
                    if (reason != null) putExtra("stop_reason",reason)
                }

            context.startService(intent)
        }
    }

    private val executor =
        Executors.newSingleThreadExecutor()

    private val recoveryTimer = java.util.concurrent.Executors.newScheduledThreadPool(2)
    private val recoveryLock = Any()
    private var notificationTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var healthTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var recoveryTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var recoveryPending = false
    private val coreEpoch = java.util.concurrent.atomic.AtomicLong(0)
    @Volatile private var everConnected = false
    @Volatile private var lastSource: String? = null
    @Volatile private var lastConnection: String? = null
    private var retryAttempt = 0
    private var networkSignature: String? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val connectivityManager: ConnectivityManager
        get() =
            getSystemService(
                ConnectivityManager::class.java
            )

    private var xrayRuntime: XrayHevRuntime? = null
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    private val tunLock = Any()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    @Volatile
    private var physicalDefaultNetwork: Network? = null

    private var physicalNetworkCallback:
            ConnectivityManager.NetworkCallback? = null


    @Volatile
    private var destroyed = false

    @Volatile
    private var isStarting = false

    @Volatile
    private var currentSessionStartId = 0

    @Volatile
    private var latestServiceStartId = 0

    override fun onCreate() {
        super.onCreate()

        wakeLock = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"VihtVPN:connected").apply { setReferenceCounted(false); acquire(600_000L) }
        createNotificationChannel()

        startForegroundNotification(
            getString(R.string.vpn_service_starting)
        )

        notificationTask = recoveryTimer.scheduleWithFixedDelay({ if(!destroyed && VpnRunStore.desired(this) && VpnStatus.state.value in setOf(VpnConnectionState.CONNECTED,VpnConnectionState.CONNECTING)) runCatching { xrayRuntime?.publishTraffic(); updateForegroundNotification("VPN работает") } },3,3,java.util.concurrent.TimeUnit.SECONDS)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        latestServiceStartId = startId

        when (intent?.action) {

            ACTION_START -> {
                everConnected = false; retryAttempt = 0
                DiagnosticLogStore.append(
                    this,
                    getString(R.string.vpn_log_start)
                )

                val config =
                    intent.getStringExtra(
                        EXTRA_CONFIG
                    )

                val connectionId =
                    intent.getStringExtra(
                        EXTRA_CONNECTION_ID
                    )

                if (
                    !config.isNullOrBlank() &&
                    !connectionId.isNullOrBlank()
                ) {

                    VpnRunStore.start(this, connectionId)
                    executor.execute {
                        startVpn(
                            config,
                            connectionId,
                            startId
                        )
                    }

                } else {
                    Log.e(
                        TAG,
                        "Missing Xray config"
                    )

                    executor.execute {
                        stopVpn(startId)
                    }
                }
            }

            ACTION_TILE -> {
                everConnected = false; retryAttempt = 0
                val selected = ConnectionStore.selected(this)
                if (selected == null) { stopSelf(startId); return START_NOT_STICKY }
                VpnRunStore.start(this, selected.id)
                VpnStatus.setActiveConnectionId(selected.id,selected.name)
                VpnStatus.set(VpnConnectionState.CONNECTING)
                executor.execute {
                    try {
                        // Reuse the authenticated saved configuration, as the normal VPN client does.
                        // The VPN server validates its expiry and credentials; the portal is not a startup dependency.
                        if (destroyed || latestServiceStartId != startId || !VpnRunStore.desired(this)) return@execute
                        val settings = JSONObject(getSharedPreferences("viht_ui_settings", Context.MODE_PRIVATE).getString("vpnSettings", "{}") ?: "{}")
                        val config = VihtVpnConfig.build(ConnectionProfileParser.parse(selected.link), settings)
                        check(XrayConfigBuilder.supported(config)) { "Новое ядро не поддерживает этот формат подключения" }
                        startVpn(config, selected.id, startId)
                    } catch (failure: Exception) {
                        if (latestServiceStartId == startId && !destroyed) {
                            VpnRunStore.stop(this)
                            stopVpn(startId)
                            VpnStatus.fail(failure.message ?: "Не удалось подключиться из шторки", false)
                        }
                    }
                }
            }
            ACTION_STOP -> {
                VpnStatus.markManualStop()
                VpnRunStore.stop(this)
                executor.execute {
                    stopVpn(startId)
                    intent.getStringExtra("stop_reason")?.let { VpnStatus.fail(it,false) }
                }
            }

            else -> {
                executor.execute {
                    val restored = runCatching { VpnRunStore.restore(this) }.getOrNull()
                    if (restored != null && VpnRunStore.desired(this)) { everConnected = VpnRunStore.verified(this); startVpn(restored.first, restored.second, startId) }
                    else { VpnRunStore.stop(this); stopVpn(startId) }
                }
            }
        }

        return if (intent?.action == ACTION_STOP) START_NOT_STICKY else START_STICKY
    }

    private fun startVpn(
        config: String,
        connectionId: String,
        startId: Int
    ) {
        synchronized(this) {

            if (!VpnLifecyclePolicy.canStart(
                    hasCommandServer = xrayRuntime != null,
                    isStarting = isStarting
                )) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "VPN is already running or starting"
                )
                return
            }

            isStarting = true
            lastSource = config; lastConnection = connectionId
            currentSessionStartId = startId

            VpnStatus.setActiveConnectionId(
                connectionId, ConnectionStore.findById(this,connectionId)?.name
            )

            VpnStatus.set(
                VpnConnectionState.CONNECTING
            )

            try {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Creating CommandServer"
                )

                check(XrayConfigBuilder.supported(config)) { "Новое ядро не поддерживает этот формат подключения" }
                startPhysicalNetworkMonitor()
                val prepared = prepareAndroidConfig(config)
                xrayRuntime = XrayHevRuntime(this).also { runtime -> runtime.start(prepared.first, prepared.second) { openXrayTun() } }
                physicalDefaultNetwork?.let { notifyDefaultInterface(it) }

                check(tunDescriptor != null) { "VPN interface was not established" }
                check(probeHealthy(prepared.second)) { "VPN не подтвердил передачу трафика" }
                if (destroyed || latestServiceStartId != startId) {
                    releaseVpnResources()
                    currentSessionStartId = 0
                    VpnStatus.setActiveConnectionId(null)
                    VpnStatus.set(VpnConnectionState.DISCONNECTED)
                    return
                }

                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Xray started"
                )

                DiagnosticLogStore.append(
                    this,
                    getString(R.string.vpn_log_connected)
                )

                VpnStatus.set(
                    VpnConnectionState.CONNECTED
                )
                everConnected = true; retryAttempt = 0
                VpnRunStore.markVerified(this)
                startHealthChecks(prepared.second, startId)
                DevicePresenceReporter.connected(this)

                if (
                    !AppVisibility.isForeground()
                ) {
                    LauncherIconManager.showGreen(
                        this
                    )
                }


                updateForegroundNotification(
                    getString(R.string.vpn_service_running)
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to start Xray",
                    e
                )

                DiagnosticLogStore.append(
                    this,
                    getString(R.string.vpn_log_connection_error, e.localizedVpnMessage(this))
                )

                if (everConnected && VpnRecoveryPolicy.allowed(VpnRunStore.desired(this), destroyed, startId, latestServiceStartId)) {
                    releaseVpnResources()
                    currentSessionStartId = 0
                    VpnStatus.set(VpnConnectionState.CONNECTING)
                    DevicePresenceReporter.stopped(this)
                    updateForegroundNotification("Восстанавливаем соединение…")
                    requestRecovery(startId)
                } else {
                    stopVpn(startId, startId)
                    if (latestServiceStartId == startId) VpnRunStore.stop(this)
                    VpnStatus.fail(e.localizedVpnMessage(this), false)
                }

            } finally {
                isStarting = false
            }
        }
    }

    private fun choosePhysicalNetwork(exclude: Network? = null): Network? {
        val candidates = connectivityManager.allNetworks.filter { network -> network != exclude && connectivityManager.getNetworkCapabilities(network)?.let { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true }
        return candidates.firstOrNull { connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true } ?: candidates.firstOrNull()
    }
    private fun startHealthChecks(port: Int, request: Int) {
        healthTask?.cancel(false)
        val epoch = coreEpoch.get()
        var failures = 0
        healthTask = recoveryTimer.scheduleWithFixedDelay({
            if (!VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId) || epoch != coreEpoch.get()) return@scheduleWithFixedDelay
            runCatching { wakeLock?.acquire(600_000L) }
            val healthy = probeHealthy(port)
            if (epoch != coreEpoch.get()) return@scheduleWithFixedDelay
            failures = if (healthy) 0 else failures + 1
            if (VpnRecoveryPolicy.failed(failures)) requestRecovery(request)
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
    }
    private fun requestRecovery(request: Int) {
        if (!everConnected || !VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) return
        if (!VpnRecoveryPolicy.enabled(JSONObject(getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).getString("vpnSettings","{}") ?: "{}"))) {
            VpnRunStore.stop(this)
            if (!executor.isShutdown) executor.execute {
                stopVpn(request)
                VpnStatus.fail("Связь потеряна. Автопереподключение выключено в настройках.",false)
            }
            return
        }
        synchronized(recoveryLock) {
            if (recoveryPending) return
            recoveryPending = true
            val delay = VpnRecoveryPolicy.delayMillis(retryAttempt)
            retryAttempt = minOf(5,retryAttempt+1)
            try { recoveryTask = recoveryTimer.schedule({
                if (!executor.isShutdown) executor.execute {
                    synchronized(recoveryLock) { recoveryTask = null; recoveryPending = false }
                    try {
                    if (!VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) return@execute
                    if (!VpnRecoveryPolicy.enabled(JSONObject(getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).getString("vpnSettings","{}") ?: "{}"))) {
                        VpnRunStore.stop(this); stopVpn(request); VpnStatus.fail("Автопереподключение выключено в настройках.",false); return@execute
                    }
                    val source = lastSource ?: return@execute
                    val id = lastConnection ?: return@execute
                    VpnStatus.set(VpnConnectionState.CONNECTING)
                    DevicePresenceReporter.stopped(this)
                    updateForegroundNotification("Восстанавливаем соединение…")
                    synchronized(this) { releaseVpnResources(); currentSessionStartId = 0; isStarting = false }
                    if (!VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) return@execute
                    if (choosePhysicalNetwork() == null) { requestRecovery(request); return@execute }
                    val permitted = kotlinx.coroutines.runBlocking { AndroidCabinetApi.request(this@AutoVlessVpnService,JSONObject().put("action","profile")) }
                    if (!VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) return@execute
                    if (!permitted.optBoolean("ok") && permitted.optBoolean("unauthorized")) {
                        VpnRunStore.stop(this); stopVpn(request); VpnStatus.fail("Войдите в аккаунт заново",false)
                        return@execute
                    }
                    // An outage of the management portal must not prevent reconnecting
                    // an already authenticated VPN configuration. The VPN server still
                    // enforces key revocation and subscription expiry itself.
                    if (permitted.optBoolean("ok")) {
                    val data = permitted.optJSONObject("data")
                    val profile = data?.optJSONObject("profile") ?: data?.optJSONObject("user")
                    if (profile == null || !SubscriptionPolicy.active(profile)) { VpnRunStore.stop(this); stopVpn(request); VpnStatus.fail("Подписка не активна",false); return@execute }
                    val device = kotlinx.coroutines.runBlocking { AndroidCabinetApi.request(this@AutoVlessVpnService,JSONObject().put("action","device-heartbeat").put("method","POST")) }
                    if (!VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) return@execute
                    if (DeviceAccessPolicy.denied(device)) { VpnRunStore.stop(this); stopVpn(request); VpnStatus.fail("Устройство отключено в подписке",false); return@execute }
                    }
                    if (VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) startVpn(source,id,request)
                    } catch (failure: Exception) {
                        DiagnosticLogStore.append(this,"Повторное восстановление VPN: " + failure.localizedVpnMessage(this))
                        if (VpnRecoveryPolicy.allowed(VpnRunStore.desired(this),destroyed,request,latestServiceStartId)) requestRecovery(request)
                    }
                }
            }, delay, java.util.concurrent.TimeUnit.MILLISECONDS) }
            catch (_: java.util.concurrent.RejectedExecutionException) { recoveryPending = false }
        }
    }

    private fun prepareAndroidConfig(source: String): Pair<String, Int> {
        val root = JSONObject(source)
        // Per-connection info logs are costly under heavy traffic; keep them opt-in.
        root.optJSONObject("log")?.put("level", if (DiagnosticLogStore.isDetailedEnabled(this)) "info" else "warn")
        // Also upgrade configurations saved before mandatory payment bypass existed.
        PaymentNetworkPolicy.apply(root)
        val network = physicalDefaultNetwork ?: choosePhysicalNetwork() ?: error("Нет физического подключения к сети")
        physicalDefaultNetwork = network
        val outbounds = root.getJSONArray("outbounds")
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(index)
            val host = outbound.optString("server")
            if (host.isNotBlank() && !host.matches(Regex("[0-9a-fA-F:.]+"))) {
                val addresses = network.getAllByName(host)
                val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: error("Не найден адрес VPN-сервера")
                outbound.put("server", address.hostAddress)
            }
        }
        val dnsServer = connectivityManager.getLinkProperties(network)?.dnsServers?.firstOrNull()?.hostAddress
        val dnsServers = root.getJSONObject("dns").getJSONArray("servers")
        for (index in 0 until dnsServers.length()) {
            val server = dnsServers.getJSONObject(index)
            if (server.optString("tag") == "bootstrap" && !dnsServer.isNullOrBlank()) server.put("server", dnsServer)
        }
        val port = ServerSocket(0).use { it.localPort }
        root.getJSONArray("inbounds").put(JSONObject().put("type", "mixed").put("tag", "viht-health").put("listen", "127.0.0.1").put("listen_port", port))
        val route = root.getJSONObject("route")
        val previous = route.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray().put(JSONObject().put("inbound", JSONArray(listOf("viht-health"))).put("action", "route").put("outbound", "proxy"))
        for (index in 0 until previous.length()) rules.put(previous.get(index))
        route.put("rules", rules)
        return root.toString() to port
    }
    private fun probeHealthy(port: Int): Boolean = listOf("https://www.gstatic.com/generate_204","https://cp.cloudflare.com/generate_204").any { url -> runCatching { verifyTunnel(port,url); true }.getOrDefault(false) }
    private fun verifyTunnel(port: Int, target: String = "https://www.gstatic.com/generate_204") {
        val connection = URL(target).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))) as HttpURLConnection
        try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("Connection", "close")
            check(connection.responseCode == 204) { "VPN-ключ не подтвердил передачу трафика" }
        } finally { connection.disconnect() }
    }

    private fun releaseVpnResources() {
        coreEpoch.incrementAndGet()
        healthTask?.cancel(false); healthTask = null
        runCatching { xrayRuntime?.stop() }
        xrayRuntime = null
        // Release the Android-owned interface before waiting for native workers.
        val descriptor = synchronized(tunLock) { tunDescriptor.also { tunDescriptor = null } }
        runCatching { descriptor?.close() }
        stopPhysicalNetworkMonitor()

    }

    private fun stopVpn(
        stopStartId: Int,
        expectedSessionStartId: Int? = null
    ) {
        synchronized(this) {
            if (
                VpnLifecyclePolicy.isStaleStop(
                    expectedSessionStartId,
                    currentSessionStartId
                )
            ) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Ignoring stale VPN stop for session $expectedSessionStartId; current session is $currentSessionStartId"
                )

                return
            }

            releaseVpnResources()

            currentSessionStartId = 0

            val serviceWillStop =
                stopSelfResult(stopStartId)

            if (serviceWillStop) {
                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
            }

            DiagnosticLogStore.append(
                this,
                getString(R.string.vpn_log_disconnected)
            )

            VpnStatus.setActiveConnectionId(
                null
            )

            VpnStatus.set(
                VpnConnectionState.DISCONNECTED
            )
            DevicePresenceReporter.stopped(this)

            if (
                !AppVisibility.isForeground()
            ) {
                LauncherIconManager.showBlue(
                    this
                )
            }

            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "VPN stopped"
            )
        }
    }

    override fun onDestroy() {
        destroyed = true
        recoveryTimer.shutdownNow()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }; wakeLock = null
        latestServiceStartId = Int.MAX_VALUE
        val descriptor = synchronized(tunLock) { tunDescriptor.also { tunDescriptor = null } }
        runCatching { descriptor?.close() }
        if (!executor.isShutdown) {
            executor.execute { synchronized(this) { releaseVpnResources() } }
            executor.shutdown()
        }
        VpnStatus.setActiveConnectionId(null)
        if (VpnStatus.state.value != VpnConnectionState.ERROR) VpnStatus.set(VpnConnectionState.DISCONNECTED)
        DevicePresenceReporter.stopped(this)
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // The started foreground VPN belongs to the service, not the closed UI task.
        super.onTaskRemoved(rootIntent)
    }
    override fun onRevoke() {
        VpnRunStore.stop(this)
        val revokedSession = currentSessionStartId
        val revokedStart = latestServiceStartId
        val descriptor = synchronized(tunLock) { tunDescriptor.also { tunDescriptor = null } }
        runCatching { descriptor?.close() }
        VpnStatus.fail("Разрешение на VPN отозвано Android или включён другой VPN", false)
        DevicePresenceReporter.stopped(this)
        if (!executor.isShutdown) executor.execute { stopVpn(revokedStart, revokedSession) }
        super.onRevoke()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return super.onBind(intent)
    }

    // ------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------

    private fun createNotificationChannel() { VpnNotification.ensureChannel(this) }

    @Synchronized private fun startForegroundNotification(
        text: String
    ) {
        lastNotificationContent = null
        startForeground(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    private var lastNotificationContent: Pair<String, String>? = null

    @Synchronized private fun updateForegroundNotification(
        text: String
    ) {
        val notification = buildNotification(text)
        val content = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() to
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        // Sending an identical notification still crosses Binder and wakes System UI.
        if (content == lastNotificationContent) return
        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            notification
        )
        lastNotificationContent = content
    }

    private fun buildNotification(text: String): android.app.Notification = VpnNotification.build(this,text)

    // ------------------------------------------------------------
    // PlatformInterface
    // ------------------------------------------------------------

    private fun openXrayTun(): Int {
        check(!destroyed && latestServiceStartId == currentSessionStartId) { "Запуск VPN отменён" }
        check(prepare(this) == null) { "VPN permission has not been granted" }
        val settings = JSONObject(getSharedPreferences("viht_ui_settings", Context.MODE_PRIVATE).getString("vpnSettings", "{}") ?: "{}")
        val builder = Builder().setSession("Viht · Xray test").setMtu(1400)
            .addAddress("172.19.0.1", 30).addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("0.0.0.0", 0).addRoute("::", 0)
            .addDnsServer(if (settings.optBoolean("dnsAdblock", true)) "94.140.14.14" else "1.1.1.1")
        // Includes our UID: Xray's child sockets must never be captured by our TUN.
        AppRoutingConfigurator.apply(this, builder, RoutingSettingsStore.load(this))
        val network = physicalDefaultNetwork ?: error("Нет физического подключения к сети")
        builder.setUnderlyingNetworks(arrayOf(network))
        val descriptor = builder.establish() ?: error("Android failed to establish VPN interface")
        synchronized(tunLock) {
            if (destroyed || latestServiceStartId != currentSessionStartId) { descriptor.close(); error("Запуск VPN отменён") }
            tunDescriptor = descriptor
        }
        return descriptor.fd
    }

    private fun startPhysicalNetworkMonitor() {
        if (physicalNetworkCallback != null) {
            return
        }

        val current =
            connectivityManager.activeNetwork

        if (current != null) {
            val caps =
                connectivityManager
                    .getNetworkCapabilities(current)

            if (
                caps != null &&
                !caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_VPN
                )
            ) {
                physicalDefaultNetwork =
                    current

                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Captured physical network before VPN: $current"
                )
            }
        }

        val request =
            NetworkRequest
                .Builder()
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
                )
                .build()

        val callback =
            object :
                ConnectivityManager.NetworkCallback() {

                override fun onAvailable(
                    network: Network
                ) {
                    val caps =
                        connectivityManager
                            .getNetworkCapabilities(network)

                    if (
                        caps?.hasTransport(
                            NetworkCapabilities.TRANSPORT_VPN
                        ) == true
                    ) {
                        Log.w(
                            TAG,
                            "Ignoring VPN network from physical monitor: $network"
                        )
                        return
                    }

                    if (physicalNetworkCallback !== this) return
                    physicalDefaultNetwork = network

                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Physical network available: $network"
                    )

                    notifyDefaultInterface(
                        network
                    )
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    if (
                        network !=
                        physicalDefaultNetwork
                    ) {
                        return
                    }

                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Physical link properties: " +
                                "network=$network " +
                                "interface=${linkProperties.interfaceName}"
                    )

                    notifyDefaultInterface(
                        network
                    )
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities:
                    NetworkCapabilities
                ) {
                    if (
                        network !=
                        physicalDefaultNetwork
                    ) {
                        return
                    }

                    notifyDefaultInterface(
                        network
                    )
                }

                override fun onLost(
                    network: Network
                ) {
                    if (
                        network !=
                        physicalDefaultNetwork
                    ) {
                        return
                    }

                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Physical network lost: $network"
                    )

                    if (physicalNetworkCallback !== this) return
                    physicalDefaultNetwork = choosePhysicalNetwork(network)
                    runCatching { setUnderlyingNetworks(physicalDefaultNetwork?.let { arrayOf(it) }) }
                    if (everConnected) requestRecovery(latestServiceStartId)

                    val replacement = physicalDefaultNetwork
                    if (replacement != null) notifyDefaultInterface(replacement)

                }
            }

        physicalNetworkCallback =
            callback

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            connectivityManager
                .registerBestMatchingNetworkCallback(
                    request,
                    callback,
                    mainHandler
                )

            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Registered best-matching physical network callback"
            )
        } else {
            connectivityManager
                .registerNetworkCallback(
                    request,
                    callback
                )

            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Registered physical network callback"
            )
        }

        physicalDefaultNetwork
            ?.let {
                notifyDefaultInterface(
                    it
                )
            }
    }

    private fun notifyDefaultInterface(network: Network) {
        val properties = connectivityManager.getLinkProperties(network) ?: return
        val name = properties.interfaceName ?: return
        if (!name.startsWith("tun")) updatePhysicalNetworkSignature(network, properties, name)
    }

    private fun updatePhysicalNetworkSignature(network: Network, properties: LinkProperties, interfaceName: String) {
        val signature = network.toString() + ":" + interfaceName + ":" + properties.linkAddresses.map { VpnRecoveryPolicy.addressKey(it.address.address) }.distinct().sorted().joinToString() + ":" + properties.dnsServers.mapNotNull { it.hostAddress }.sorted().joinToString()
        val changed = networkSignature != null && networkSignature != signature
        networkSignature = signature
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
        if (changed && everConnected && VpnStatus.state.value == VpnConnectionState.CONNECTED) requestRecovery(latestServiceStartId)
    }

    private fun stopPhysicalNetworkMonitor() {
        val callback =
            physicalNetworkCallback

        if (callback != null) {
            try {
                connectivityManager
                    .unregisterNetworkCallback(
                        callback
                    )
            } catch (_: Exception) {
            }
        }

        physicalNetworkCallback =
            null

        physicalDefaultNetwork =
            null
    }

}
