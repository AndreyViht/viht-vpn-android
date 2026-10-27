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
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
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
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class AutoVlessVpnService :
    VpnService(),
    PlatformInterface {

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
        fun stop(context: Context) {
            VpnRunStore.stop(context)
            VpnStatus.markManualStop()
            val intent =
                Intent(
                    context,
                    AutoVlessVpnService::class.java
                ).apply {
                    action = ACTION_STOP
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

    private var commandServer: CommandServer? = null
    private var singBoxLogClient: SingBoxLogClient? = null
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    private val tunLock = Any()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    @Volatile
    private var physicalDefaultNetwork: Network? = null

    private var physicalNetworkCallback:
            ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var interfaceListener:
            InterfaceUpdateListener? = null

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

        notificationTask = recoveryTimer.scheduleWithFixedDelay({ if(!destroyed && VpnRunStore.desired(this) && VpnStatus.state.value in setOf(VpnConnectionState.CONNECTED,VpnConnectionState.CONNECTING)) runCatching { updateForegroundNotification("VPN работает") } },3,3,java.util.concurrent.TimeUnit.SECONDS)
        try {
            setupLibbox()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Libbox setup failed",
                e
            )
        }
    }

    private fun setupLibbox() {
        val baseDir =
            File(filesDir, "sing-box").apply {
                mkdirs()
            }

        val workingDir =
            File(baseDir, "working").apply {
                mkdirs()
            }

        val tempDir =
            File(cacheDir, "sing-box").apply {
                mkdirs()
            }

        val options =
            SetupOptions().apply {
                basePath =
                    baseDir.absolutePath

                workingPath =
                    workingDir.absolutePath

                tempPath =
                    tempDir.absolutePath

                fixAndroidStack =
                    true

                commandServerListenPort =
                    0

                commandServerSecret =
                    Libbox.randomHex(32).value

                logMaxLines =
                    500

                debug =
                    BuildConfig.DEBUG
            }

        Libbox.setup(options)

        Log.d(
            TAG,
            "Libbox setup complete"
        )
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
                        "Missing sing-box config"
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
                        LibboxValidator.validate(config).getOrThrow()
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
                    hasCommandServer = commandServer != null,
                    isStarting = isStarting
                )) {
                Log.d(
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
                Log.d(
                    TAG,
                    "Creating CommandServer"
                )

                val server =
                    CommandServer(
                        createCommandServerHandler(startId),
                        this
                    )

                commandServer =
                    server

                Log.d(
                    TAG,
                    "Starting CommandServer"
                )

                server.start()

                Log.d(
                    TAG,
                    "Starting physical network monitor"
                )

                startPhysicalNetworkMonitor()

                Log.d(
                    TAG,
                    "Starting sing-box service"
                )

                val prepared = prepareAndroidConfig(config)
                server.startOrReloadService(
                    prepared.first,
                    OverrideOptions()
                )

                check(tunDescriptor != null) { "VPN interface was not established" }
                check(probeHealthy(prepared.second)) { "VPN не подтвердил передачу трафика" }
                if (destroyed || latestServiceStartId != startId) {
                    releaseVpnResources()
                    currentSessionStartId = 0
                    VpnStatus.setActiveConnectionId(null)
                    VpnStatus.set(VpnConnectionState.DISCONNECTED)
                    return
                }

                singBoxLogClient =
                    SingBoxLogClient(this).also {
                        it.start()
                    }

                Log.d(
                    TAG,
                    "sing-box started"
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
                    "Failed to start sing-box",
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
                    if (device.optBoolean("ok") && device.has("device_allowed") && !device.optBoolean("device_allowed")) { VpnRunStore.stop(this); stopVpn(request); VpnStatus.fail("Устройство отключено в подписке",false); return@execute }
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
        // Release the Android-owned interface before waiting for native workers.
        val descriptor = synchronized(tunLock) { tunDescriptor.also { tunDescriptor = null } }
        runCatching { descriptor?.close() }
        stopPhysicalNetworkMonitor()

        singBoxLogClient
            ?.stop()

        singBoxLogClient =
            null

        try {
            commandServer
                ?.closeService()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "closeService failed",
                e
            )
        }

        try {
            commandServer
                ?.close()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "CommandServer close failed",
                e
            )
        }

        commandServer =
            null


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
                Log.d(
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

            Log.d(
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

    private fun startForegroundNotification(
        text: String
    ) {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    private fun updateForegroundNotification(
        text: String
    ) {
        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    private fun buildNotification(text: String): android.app.Notification = VpnNotification.build(this,text)

    // ------------------------------------------------------------
    // PlatformInterface
    // ------------------------------------------------------------

    override fun usePlatformAutoDetectInterfaceControl(): Boolean {
        Log.d(TAG, "usePlatformAutoDetectInterfaceControl CALLED -> true")
        return true
    }

    override fun autoDetectInterfaceControl(
        fd: Int
    ) {
        val network =
            physicalDefaultNetwork

        Log.d(
            TAG,
            "autoDetectInterfaceControl: fd=$fd network=$network"
        )

        check(protect(fd)) { "Android не разрешил исключить исходящий сокет из VPN" }

        if (network == null) {
            Log.e(
                TAG,
                "autoDetectInterfaceControl: no physical network for fd=$fd"
            )
            error("Нет физической сети для подключения VPN")
        }

        val duplicate =
            ParcelFileDescriptor.fromFd(fd)

        try {
            network.bindSocket(
                duplicate.fileDescriptor
            )

            Log.d(
                TAG,
                "Network.bindSocket OK: fd=$fd network=$network"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Network.bindSocket failed: fd=$fd network=$network",
                e
            )
            throw e
        } finally {
            duplicate.close()
        }
    }

    override fun openTun(
        options: TunOptions
    ): Int {
        check(!destroyed && latestServiceStartId == currentSessionStartId) { "Запуск VPN отменён" }
        if (
            prepare(this) != null
        ) {
            error(
                "VPN permission has not been granted"
            )
        }

        val builder =
            Builder()
                .setSession(
                    "Viht VPN"
                )
                .setMtu(
                    options.mtu
                )

        val ipv4 =
            options.inet4Address

        while (ipv4.hasNext()) {
            val address =
                ipv4.next()

            builder.addAddress(
                address.address(),
                address.prefix()
            )
        }

        val ipv6 =
            options.inet6Address

        while (ipv6.hasNext()) {
            val address =
                ipv6.next()

            builder.addAddress(
                address.address(),
                address.prefix()
            )
        }

        if (
            options.autoRoute
        ) {

            val ipv4Routes =
                options.inet4RouteRange

            if (
                ipv4Routes.hasNext()
            ) {

                while (
                    ipv4Routes.hasNext()
                ) {
                    val route =
                        ipv4Routes.next()

                    builder.addRoute(
                        route.address(),
                        route.prefix()
                    )
                }

            } else {

                builder.addRoute(
                    "0.0.0.0",
                    0
                )
            }

            val ipv6Routes =
                options.inet6RouteRange

            if (
                ipv6Routes.hasNext()
            ) {

                while (
                    ipv6Routes.hasNext()
                ) {
                    val route =
                        ipv6Routes.next()

                    builder.addRoute(
                        route.address(),
                        route.prefix()
                    )
                }
            }
        }

        // Capture IPv6 too; a missing IPv6 route must not bypass the VPN.
        builder.addRoute("::", 0)

        builder.addDnsServer(
            "1.1.1.1"
        )

        AppRoutingConfigurator.apply(
            this,
            builder,
            RoutingSettingsStore.load(this)
        )

        val descriptor =
            builder.establish()
                ?: error(
                    "Android failed to establish VPN interface"
                )

        synchronized(tunLock) {
            if (destroyed || latestServiceStartId != currentSessionStartId) { descriptor.close(); error("Запуск VPN отменён") }
            tunDescriptor = descriptor
        }

        Log.d(
            TAG,
            "TUN established, fd=${descriptor.fd}"
        )

        return descriptor.fd
    }

    /*
     * On Android, do not use /proc to determine
     * the process that created the connection.
     */
    override fun useProcFS(): Boolean =
        false

    override fun includeAllNetworks(): Boolean =
        false

    override fun underNetworkExtension(): Boolean =
        false

    override fun clearDNSCache() {
    }

    override fun readWIFIState(): WIFIState? =
        null

    override fun localDNSTransport(): LocalDNSTransport? =
        null

    override fun systemCertificates(): StringIterator? =
        null

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < 29) return ConnectionOwner().apply { userId = Process.INVALID_UID; userName = ""; setAndroidPackageNames(StringArray(emptyList<String>().iterator())) }
        try {
            val uid =
                connectivityManager.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(
                        sourceAddress,
                        sourcePort
                    ),
                    InetSocketAddress(
                        destinationAddress,
                        destinationPort
                    )
                )

            if (uid == Process.INVALID_UID) {
                error(
                    "android: connection owner not found"
                )
            }

            val packages =
                packageManager.getPackagesForUid(
                    uid
                )

            return ConnectionOwner().apply {
                userId =
                    uid

                userName =
                    packages?.firstOrNull()
                        ?: ""

                setAndroidPackageNames(
                    StringArray(
                        packages
                            ?.toList()
                            ?.iterator()
                            ?: emptyList<String>()
                                .iterator()
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "findConnectionOwner failed",
                e
            )

            throw e
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {

        val result =
            mutableListOf<LibboxNetworkInterface>()

        for (
        network in
        connectivityManager.allNetworks
        ) {

            val properties =
                connectivityManager
                    .getLinkProperties(network)
                    ?: continue

            val capabilities =
                connectivityManager
                    .getNetworkCapabilities(network)
                    ?: continue

            val interfaceName =
                properties.interfaceName
                    ?: continue

            if (
                interfaceName.startsWith("tun") ||
                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_VPN
                )
            ) {
                continue
            }

            val interfaceIndex =
                resolveInterfaceIndex(
                    properties,
                    interfaceName
                )

            if (interfaceIndex <= 0) {
                Log.w(
                    TAG,
                    "Skipping interface $interfaceName: " +
                            "unable to resolve interface index"
                )
                continue
            }

            val boxInterface =
                LibboxNetworkInterface()

            boxInterface.name =
                interfaceName

            boxInterface.index =
                interfaceIndex

            boxInterface.mtu =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && properties.mtu > 0) {
                    properties.mtu
                } else {
                    1500
                }

            boxInterface.type =
                when {

                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    ) ->
                        Libbox.InterfaceTypeWIFI

                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_CELLULAR
                    ) ->
                        Libbox.InterfaceTypeCellular

                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_ETHERNET
                    ) ->
                        Libbox.InterfaceTypeEthernet

                    else ->
                        Libbox.InterfaceTypeOther
                }

            boxInterface.metered =
                !capabilities.hasCapability(
                    NetworkCapabilities
                        .NET_CAPABILITY_NOT_METERED
                )

            var flags = 0

            if (
                capabilities.hasCapability(
                    NetworkCapabilities
                        .NET_CAPABILITY_INTERNET
                )
            ) {
                flags =
                    flags or
                            OsConstants.IFF_UP or
                            OsConstants.IFF_RUNNING
            }

            boxInterface.flags =
                flags

            val addresses =
                properties
                    .linkAddresses
                    .mapNotNull { linkAddress ->
                        val host =
                            linkAddress.address.hostAddress
                                ?.substringBefore("%")

                        if (host.isNullOrBlank()) {
                            null
                        } else {
                            "$host/${linkAddress.prefixLength}"
                        }
                    }

            boxInterface.addresses =
                StringArray(
                    addresses.iterator()
                )

            val dnsServers =
                properties
                    .dnsServers
                    .mapNotNull {
                        it.hostAddress
                    }

            boxInterface.dnsServer =
                StringArray(
                    dnsServers.iterator()
                )

            Log.d(
                TAG,
                "Interface from LinkProperties: " +
                        "$interfaceName " +
                        "index=$interfaceIndex " +
                        "mtu=${boxInterface.mtu} " +
                        "type=${boxInterface.type} " +
                        "addresses=${addresses.size}"
            )

            result.add(
                boxInterface
            )
        }

        Log.d(
            TAG,
            "getInterfaces(): ${result.size} interfaces"
        )

        return InterfaceArray(
            result.iterator()
        )
    }

    override fun startDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener?
    ) {
        if (listener == null) {
            return
        }

        Log.d(
            TAG,
            "libbox registered default interface listener"
        )

        interfaceListener =
            listener

        val network =
            physicalDefaultNetwork

        if (network != null) {
            notifyDefaultInterface(
                network
            )
        } else {
            Log.w(
                TAG,
                "No physical default network yet when libbox listener registered"
            )
        }
    }

    override fun closeDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener?
    ) {
        Log.d(
            TAG,
            "libbox closed default interface listener"
        )

        if (
            interfaceListener === listener ||
            listener == null
        ) {
            interfaceListener =
                null
        }
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

                Log.d(
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

                    Log.d(
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

                    Log.d(
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

                    Log.d(
                        TAG,
                        "Physical network lost: $network"
                    )

                    if (physicalNetworkCallback !== this) return
                    physicalDefaultNetwork = choosePhysicalNetwork(network)
                    runCatching { setUnderlyingNetworks(physicalDefaultNetwork?.let { arrayOf(it) }) }
                    if (everConnected) requestRecovery(latestServiceStartId)

                    val replacement = physicalDefaultNetwork
                    if (replacement != null) notifyDefaultInterface(replacement)
                    else interfaceListener?.updateDefaultInterface("",-1,false,false)
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

            Log.d(
                TAG,
                "Registered best-matching physical network callback"
            )
        } else {
            connectivityManager
                .registerNetworkCallback(
                    request,
                    callback
                )

            Log.d(
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

    private fun resolveInterfaceIndex(
        properties: LinkProperties,
        interfaceName: String
    ): Int {
        val osIndex =
            try {
                Os.if_nametoindex(
                    interfaceName
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Os.if_nametoindex($interfaceName) failed",
                    e
                )
                0
            }

        if (osIndex > 0) {
            Log.d(
                TAG,
                "Interface index from Os: " +
                        "$interfaceName index=$osIndex"
            )
            return osIndex
        }

        for (linkAddress in properties.linkAddresses) {
            val address =
                linkAddress.address

            if (address is Inet6Address) {
                val scopeId =
                    address.scopeId

                Log.d(
                    TAG,
                    "IPv6 scope candidate: " +
                            "$interfaceName " +
                            "address=${address.hostAddress} " +
                            "scopeId=$scopeId"
                )

                if (scopeId > 0) {
                    Log.d(
                        TAG,
                        "Interface index from IPv6 scope: " +
                                "$interfaceName index=$scopeId"
                    )
                    return scopeId
                }
            }
        }

        Log.e(
            TAG,
            "Unable to resolve interface index for $interfaceName"
        )

        return 0
    }

    private fun notifyDefaultInterface(
        network: Network
    ) {
        val listener =
            interfaceListener
                ?: return

        val properties =
            connectivityManager
                .getLinkProperties(network)
                ?: run {
                    Log.w(
                        TAG,
                        "Default interface: LinkProperties unavailable " +
                                "for network=$network"
                    )
                    return
                }

        val interfaceName =
            properties.interfaceName

        if (interfaceName.isNullOrBlank()) {
            Log.w(
                TAG,
                "Default interface: interfaceName unavailable " +
                        "for network=$network"
            )
            return
        }

        if (
            interfaceName.startsWith("tun")
        ) {
            Log.w(
                TAG,
                "Ignoring VPN interface as physical default: $interfaceName"
            )
            return
        }

        /*
         * On some Honor devices, the rmnet interface name is visible via
         * ConnectivityManager, while NetworkInterface and if_nametoindex()
         * do not provide an index. In that case, use the numeric scopeId
         * of the IPv6 address from LinkProperties. For link-local IPv6,
         * it corresponds to the system interface index.
         */
        val interfaceIndex =
            resolveInterfaceIndex(
                properties,
                interfaceName
            )

        if (interfaceIndex <= 0) {
            Log.e(
                TAG,
                "Default interface unresolved: " +
                        "$interfaceName ifindex=$interfaceIndex"
            )
            return
        }

        Log.d(
            TAG,
            "Default physical interface BEFORE update: " +
                    "$interfaceName index=$interfaceIndex"
        )

        val signature = network.toString() + ":" + interfaceName + ":" + properties.linkAddresses.map { VpnRecoveryPolicy.addressKey(it.address.address) }.distinct().sorted().joinToString() + ":" + properties.dnsServers.mapNotNull { it.hostAddress }.sorted().joinToString()
        val changed = networkSignature != null && networkSignature != signature
        networkSignature = signature
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
        if (changed && everConnected && VpnStatus.state.value == VpnConnectionState.CONNECTED) requestRecovery(latestServiceStartId)
        listener.updateDefaultInterface(
            interfaceName,
            interfaceIndex,
            false,
            false
        )

        Log.d(
            TAG,
            "Default physical interface AFTER update: " +
                    "$interfaceName index=$interfaceIndex"
        )
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

        interfaceListener =
            null
    }

    override fun sendNotification(
        notification: Notification?
    ) {
        Log.d(
            TAG,
            "libbox notification: " +
                    "${notification?.title}: " +
                    "${notification?.body}"
        )
    }

    // ------------------------------------------------------------
    // CommandServerHandler
    // ------------------------------------------------------------

    private fun createCommandServerHandler(
        sessionStartId: Int
    ): CommandServerHandler =
        object : CommandServerHandler {
            private val handlerEpoch = coreEpoch.get()
            override fun serviceStop() {
                if (handlerEpoch != coreEpoch.get()) return
                val stopStartId =
                    latestServiceStartId

                if (everConnected && VpnRecoveryPolicy.allowed(VpnRunStore.desired(this@AutoVlessVpnService),destroyed,sessionStartId,latestServiceStartId)) requestRecovery(sessionStartId)
                else executor.execute { stopVpn(stopStartId, sessionStartId) }
            }

            override fun serviceReload() {
                Log.d(
                    TAG,
                    "serviceReload requested"
                )
            }

            override fun getSystemProxyStatus():
                    SystemProxyStatus? =
                null

            override fun setSystemProxyEnabled(
                isEnabled: Boolean
            ) {
            }

            override fun writeDebugMessage(
                message: String?
            ) {
                Log.d(
                    TAG,
                    message ?: ""
                )
            }
        }

    // ------------------------------------------------------------
    // libbox iterators
    // ------------------------------------------------------------

    private class InterfaceArray(
        private val iterator:
        Iterator<LibboxNetworkInterface>
    ) : NetworkInterfaceIterator {

        override fun hasNext(): Boolean =
            iterator.hasNext()

        override fun next():
                LibboxNetworkInterface =
            iterator.next()
    }

    private class StringArray(
        private val iterator:
        Iterator<String>
    ) : StringIterator {

        override fun len(): Int =
            0

        override fun hasNext(): Boolean =
            iterator.hasNext()

        override fun next(): String =
            iterator.next()
    }

}
