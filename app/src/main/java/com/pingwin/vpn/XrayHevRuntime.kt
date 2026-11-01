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
        @Volatile var lastEngine = "VPN engine has not started in this process"
        @Volatile var active: XrayHevRuntime? = null
            private set
        fun executable(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libvihtxray.so")
        fun available(context: Context, source: String) = executable(context).isFile && XrayConfigBuilder.supported(source)
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

    private var process: java.lang.Process? = null
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
            lastEngine = "Xray 26.10.31 + hev-socks5-tunnel 2.19.3 (experimental)"
            DiagnosticLogStore.append(context, "VPN engine: Xray 26.10.31 + hev-socks5-tunnel 2.19.3 (experimental)")
        } catch (failure: Exception) { stop(); throw failure }
    }

    /** Also used by the isolated runtime test; no Android VPN/TUN is established here. */
    fun startCore(config: JSONObject, readyPort: Int) {
        check(process == null)
        configFile.writeText(config.toString())
        try {
            process = ProcessBuilder(executable(context).absolutePath, "run", "-config", configFile.absolutePath)
                .redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
            val deadline = SystemClock.elapsedRealtime() + 5000
            var ready = false
            while (SystemClock.elapsedRealtime() < deadline && process?.isAlive == true) {
                ready = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", readyPort), 150) }; true }.getOrDefault(false)
                if (ready) break
                SystemClock.sleep(30)
            }
            check(ready && process?.isAlive == true) { "Новое ядро Xray не запустилось; требуется диагностика тестовой версии" }
        } catch (failure: Exception) { stop(); throw failure }
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

    fun diagnosticSnapshot(): String {
        val running = process?.isAlive == true
        // Child RSS and CPU ticks are distinct from the main Android process's PSS/CPU.
        val pid = if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { process?.pid() }.getOrNull() else null
        val details = if (pid != null) runCatching {
            val status = File("/proc/$pid/status").readLines().filter { it.startsWith("VmRSS:") || it.startsWith("Threads:") }.joinToString("; ")
            val fields = File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')
            "pid=$pid; $status; CPU ticks=${fields[11].toLong() + fields[12].toLong()}; clock ticks/sec=${android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)}"
        }.getOrElse { "child counters unavailable" } else "child counters unavailable on this Android version"
        return "Xray + hev active: $running; $details"
    }

    fun stop() {
        if (active === this) active = null
        if (hevStarted) runCatching { TProxyService.TProxyStopService() }
        hevStarted = false
        process?.let { child ->
            child.destroy()
            if (!child.waitFor(1500, TimeUnit.MILLISECONDS)) child.destroyForcibly().waitFor(1500, TimeUnit.MILLISECONDS)
        }
        process = null
        configFile.delete(); hevFile.delete()
    }
}
