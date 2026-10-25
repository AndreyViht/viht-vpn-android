package com.pingwin.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.libbox.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy

/** Starts the actual packaged core with local-only inbounds. Never creates a VPN/TUN. */
@RunWith(AndroidJUnit4::class)
class CoreStartupTest {
    private fun stub(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        when (method.name) {
            "openTun" -> error("This test must never create a TUN")
            "toString" -> "CoreStartupTestPlatform"
            "hashCode" -> 1
            "equals" -> false
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Void.TYPE -> null
                String::class.java -> ""
                else -> if (method.returnType.isInterface) stub(method.returnType) else null
            }
        }
    }
    @Test fun authValidationAndRefreshUseOwnDomain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.assets.open("auth-config.json").bufferedReader().use { JSONObject(it.readText()) }
        val base = config.getString("authUrl")
        assertEquals("https://anviht.ru/auth", base)
        val headers = mapOf("apikey" to config.getString("anonKey"), "Connection" to "close")
        val user = VihtHttp.request(base + "/user", headers = headers)
        assertEquals(401, user.status)
        assertTrue(JSONObject(user.body).has("error_code"))
        val refresh = VihtHttp.request(base + "/token?grant_type=refresh_token", "POST", headers,
            JSONObject().put("refresh_token", "invalid-connectivity-probe").toString())
        assertEquals(400, refresh.status)
        assertTrue(JSONObject(refresh.body).has("error_code"))
    }
    @Test fun actualCoreStartsAndroidDnsAndProxyConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "core-startup-test").apply { mkdirs() }
        Libbox.setup(SetupOptions().apply {
            basePath = directory.absolutePath; workingPath = directory.absolutePath; tempPath = directory.absolutePath
            commandServerListenPort = 0; commandServerSecret = Libbox.randomHex(32).value; logMaxLines = 100
        })
        for (transport in listOf("ws", "grpc")) {
            for (filtered in listOf(false, true)) {
                val profile = ConnectionProfileParser.parse("vless://11111111-1111-4111-8111-111111111111@203.0.113.1:443?security=tls&sni=example.com&type=$transport&path=%2Fvpn&serviceName=test")
                val config = JSONObject(VihtVpnConfig.build(profile, JSONObject().put("dnsAdblock", filtered)))
                // No real key, TUN or system routing: exercise core lifecycle and DNS initialization only.
                config.put("inbounds", JSONArray().put(JSONObject().put("type", "mixed").put("listen", "127.0.0.1").put("listen_port", 0)))
                config.getJSONObject("route").put("auto_detect_interface", false)
                Libbox.checkConfig(config.toString())
                val server = CommandServer(stub(CommandServerHandler::class.java) as CommandServerHandler, stub(PlatformInterface::class.java) as PlatformInterface)
                try { server.start(); server.startOrReloadService(config.toString(), OverrideOptions()) }
                finally { runCatching { server.closeService() }; server.close() }
            }
        }
    }
}
