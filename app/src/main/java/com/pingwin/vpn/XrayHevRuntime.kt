package com.pingwin.vpn

import android.content.Context
import android.os.SystemClock
import hev.htproxy.TProxyService
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Xray runs in its own process: no competing gomobile runtimes in the UI process. */
internal class XrayHevRuntime(private val context: Context) {
    companion object {
        @Volatile var lastEngine = "Xray 26.10.31 + hev-socks5-tunnel 2.19.3"
        @Volatile var active: XrayHevRuntime? = null
            private set
        fun available(context: Context, source: String) = XrayConfigBuilder.supported(source)
        fun hevConfig(port: Int, username: String, password: String): String {
            require(port in 1..65535)
            require(username.matches(Regex("[A-Za-z0-9]+")) && password.matches(Regex("[A-Za-z0-9]+")))
            return """
                tunnel:
                  mtu: 1400
                  ipv4: 172.19.0.1
                  ipv6: 'fdfe:dcba:9876::1'
                socks5:
                  address: 127.0.0.1
                  port: $port
                  udp: 'udp'
                  username: '$username'
                  password: '$password'
                misc:
                  log-level: warn
                  log-file: null
                  connect-timeout: 10000
                  tcp-read-write-timeout: 300000
                  udp-read-write-timeout: 60000
            """.trimIndent()
        }
    }

    @Volatile private var coreBinder: android.os.IBinder? = null
    private var binding: android.content.ServiceConnection? = null
    private val directory = File(context.noBackupFilesDir, "xray-hev").apply { mkdirs() }
    private val configFile = File(directory, "config.json")
    private val hevFile = File(directory, "hev.yml")
    private var lastTime = 0L
    private var lastUp = 0L
    private var lastDown = 0L
    private var hevStarted = false

    fun start(source: String, healthPort: Int, tun: () -> Int) {
        val socksPort = ServerSocket(0).use { it.localPort }
        val username = UUID.randomUUID().toString().replace("-", "")
        val password = UUID.randomUUID().toString().replace("-", "")
        val config = XrayConfigBuilder.build(source, socksPort, healthPort, username, password)
        startCore(config, socksPort)
        try {
            hevFile.writeText(hevConfig(socksPort, username, password))
            check(TProxyService.TProxyStartService(hevFile.absolutePath, tun())) { "Не удалось запустить hev tunnel" }
            hevStarted = true
            SystemClock.sleep(100)
            check(TProxyService.TProxyIsRunning()) { "hev tunnel завершился при запуске" }
            lastTime = SystemClock.elapsedRealtime()
            active = this
            lastEngine = "Xray 26.10.31 + hev-socks5-tunnel 2.19.3 "
            DiagnosticLogStore.append(context, "VPN engine: Xray 26.10.31 + hev-socks5-tunnel 2.19.3 ")
        } catch (failure: Exception) { stop(); throw failure }
    }

    /** Also used by the isolated runtime test; no Android VPN/TUN is established here. */
    fun startCore(config: JSONObject, readyPort: Int) {
        check(binding == null)
        val connected = java.util.concurrent.CountDownLatch(1)
        val service = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) { coreBinder = binder; connected.countDown() }
            override fun onServiceDisconnected(name: android.content.ComponentName?) { coreBinder = null }
        }
        binding = service
        try {
            check(context.bindService(android.content.Intent(context, XrayCoreService::class.java), service, Context.BIND_AUTO_CREATE)) { "Не удалось запустить Xray" }
            check(connected.await(8, TimeUnit.SECONDS)) { "Xray не ответил при запуске" }
            transact(1, config.toString())
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", readyPort), 1500) }
        } catch (failure: Exception) { stop(); throw failure }
    }

    private fun transact(code: Int, config: String? = null): String? {
        val data = android.os.Parcel.obtain(); val reply = android.os.Parcel.obtain()
        try {
            data.writeInterfaceToken("ru.anviht.xray")
            if (config != null) data.writeString(config)
            check(coreBinder?.transact(code, data, reply, 0) == true) { "Нет связи с Xray" }
            reply.readException()
            return if (code == 3) reply.readString() else null
        } finally { data.recycle(); reply.recycle() }
    }

    fun publishTraffic() {
        if (!hevStarted || !TProxyService.TProxyIsRunning()) return
        val stats = TProxyService.TProxyGetStats()
        if (stats.size != 4) return
        val now = SystemClock.elapsedRealtime()
        val seconds = (now - lastTime).coerceAtLeast(1) / 1000.0
        val up = stats[1]; val down = stats[3]
        VpnStatus.traffic(JSONObject().put("bytesSent", up).put("bytesReceived", down)
            .put("speedUp", (up - lastUp).coerceAtLeast(0) / seconds / 1048576.0)
            .put("speedDown", (down - lastDown).coerceAtLeast(0) / seconds / 1048576.0))
        lastTime = now; lastUp = up; lastDown = down
    }

    fun diagnosticSnapshot(): String = runCatching { transact(3) ?: "Xray" }.getOrDefault("Xray недоступен")

    fun stop() {
        if (active === this) active = null
        if (hevStarted) runCatching { TProxyService.TProxyStopService() }
        hevStarted = false
        runCatching { if (coreBinder != null) transact(2) }
        coreBinder = null
        binding?.let { runCatching { context.unbindService(it) } }; binding = null
        configFile.delete(); hevFile.delete()
    }
}
