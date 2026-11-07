package com.pingwin.vpn

import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hev.htproxy.TProxyService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

/** Native traffic test on an emulator. No VpnService, no system routes, no remote credentials. */
@RunWith(AndroidJUnit4::class)
class XrayHevRuntimeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun port() = ServerSocket(0).use { it.localPort }
    private fun source(network: String) = VihtVpnConfig.build(ConnectionProfileParser.parse(
        "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com&type=$network&path=%2Fvpn&serviceName=grpc"
    ), JSONObject())

    @Test fun startupFailureCleansUpCoreForTheNextAttempt() {
        val runtime = XrayHevRuntime(context)
        val socks = port()
        val valid = XrayConfigBuilder.build(source("ws"), socks, port(), "user", "secret")
        val invalid = JSONObject(valid.toString())
        invalid.getJSONArray("outbounds").getJSONObject(0).put("protocol", "invalid-fixture-protocol")
        try {
            val failure = runCatching { runtime.startCore(invalid, socks) }.exceptionOrNull()
            assertTrue(failure is XrayHevRuntime.StartupFailure)
            runtime.startCore(valid, socks)
        } finally {
            runtime.stop()
        }
    }
    @Test fun packagedXrayValidatesOurActualWsAndGrpcConfigurations() {
        for (transport in listOf("ws", "grpc")) {
            val config = File(context.cacheDir, "xray-validation-$transport.json")
            try {
                config.writeText(XrayConfigBuilder.build(source(transport), port(), port(), "user", "secret").toString())
                val runtime = XrayHevRuntime(context)
                val value = JSONObject(config.readText())
                try { runtime.startCore(value, value.getJSONArray("inbounds").getJSONObject(0).getInt("port")) }
                finally { runtime.stop() }
            } finally { config.delete() }
        }
    }

    @Test fun udpPacketCrossesHevAndPackagedXrayAndReturnsWithoutAnyTunInterface() {
        val socks = port(); val health = port()
        val runtime = XrayHevRuntime(context)
        val config = XrayConfigBuilder.build(source("ws"), socks, health, "user", "secret")
        config.getJSONArray("outbounds").put(0, JSONObject().put("tag", "proxy").put("protocol", "freedom"))
        val echo = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 10000 }
        config.getJSONArray("outbounds").put(JSONObject().put("tag", "udp-echo").put("protocol", "freedom")
            .put("settings", JSONObject().put("redirect", "127.0.0.1:${echo.localPort}")))
        config.getJSONObject("routing").put("rules", JSONArray().put(JSONObject().put("type", "field").put("network", "udp").put("outboundTag", "udp-echo")))
        val a = FileDescriptor(); val b = FileDescriptor()
        val worker = Executors.newSingleThreadExecutor()
        val hevFile = File(context.cacheDir, "hev-runtime-fixture.yml")
        var started = false
        try {
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, a, b)
            Os.setsockoptTimeval(b, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(10000))
            runtime.startCore(config, socks)
            val response = worker.submit<Boolean> {
                val packet = DatagramPacket(ByteArray(1024), 1024)
                echo.receive(packet)
                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                echo.send(DatagramPacket(payload, payload.size, packet.address, packet.port))
                String(payload, Charsets.US_ASCII) == "viht-hev-xray-test"
            }
            hevFile.writeText(XrayHevRuntime.hevConfig(socks, "user", "secret"))
            // Dup into a Java-owned ParcelFileDescriptor exposes a numeric descriptor safely.
            android.os.ParcelFileDescriptor.dup(a).use { descriptor ->
                assertTrue(TProxyService.TProxyStartService(hevFile.absolutePath, descriptor.fd))
                started = true
                android.os.SystemClock.sleep(100)
                assertTrue(TProxyService.TProxyIsRunning())
                val payload = "viht-hev-xray-test".toByteArray(Charsets.US_ASCII)
                val packet = udpPacket(payload)
                Os.write(b, packet, 0, packet.size)
                assertTrue("UDP payload did not reach the local echo endpoint", response.get(12, TimeUnit.SECONDS))
                val received = ByteArray(2048)
                val deadline = android.os.SystemClock.elapsedRealtime() + 10000
                var returned = false
                var lastFrame = "none"
                // IPv6 neighbour discovery can precede the UDP response on a dual-stack TUN.
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    val size = Os.read(b, received, 0, received.size)
                    lastFrame = received.take(minOf(size, 64)).joinToString("") { "%02x".format(it.toInt() and 255) }
                    if (size < 28 || (received[0].toInt() ushr 4) != 4 || received[9].toInt() != 17) continue
                    val header = (received[0].toInt() and 15) * 4
                    fun short(offset: Int) = ((received[offset].toInt() and 255) shl 8) or (received[offset + 1].toInt() and 255)
                    assertTrue("Truncated IPv4 UDP header: $lastFrame", header >= 20 && header + 8 <= size)
                    if (short(header + 2) != 32123) continue
                    val length = short(header + 4)
                    val totalLength = short(2)
                    assertTrue("Malformed IPv4 UDP response: $lastFrame", length >= 8 && totalLength == header + length && totalLength <= size)
                    assertArrayEquals(payload, received.copyOfRange(header + 8, header + length))
                    returned = true
                    break
                }
                assertTrue("No returned IPv4 UDP response; last frame=$lastFrame", returned)
                val stats = TProxyService.TProxyGetStats()
                assertEquals(4, stats.size)
                assertTrue(stats[1] > 0 && stats[3] > 0)
                TProxyService.TProxyStopService(); started = false
            }
        } finally {
            if (started) TProxyService.TProxyStopService()
            runtime.stop(); echo.close(); worker.shutdownNow(); hevFile.delete()
            runCatching { Os.close(a) }; runCatching { Os.close(b) }
        }
        assertFalse(TProxyService.TProxyIsRunning())
    }

    private fun udpPacket(payload: ByteArray): ByteArray {
        val packet = ByteArray(28 + payload.size)
        fun short(offset: Int, value: Int) { packet[offset] = (value ushr 8).toByte(); packet[offset + 1] = value.toByte() }
        packet[0] = 0x45; short(2, packet.size); packet[8] = 64; packet[9] = 17
        byteArrayOf(172.toByte(), 19, 0, 2).copyInto(packet, 12)
        byteArrayOf(198.toByte(), 18, 0, 100).copyInto(packet, 16)
        short(20, 32123); short(22, 9000); short(24, 8 + payload.size)
        payload.copyInto(packet, 28)
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((packet[i].toInt() and 255) shl 8) or (packet[i + 1].toInt() and 255)
        while (sum ushr 16 != 0) sum = (sum and 65535) + (sum ushr 16)
        short(10, sum.inv() and 65535)
        return packet
    }
}
