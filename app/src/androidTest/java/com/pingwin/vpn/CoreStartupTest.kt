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
    @Test fun systemBackClosesWebDialogInsteadOfMinimizingTheApplication() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val activity=instrumentation.startActivitySync(android.content.Intent(context,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun browser(view: android.view.View): android.webkit.WebView? {
            if(view is android.webkit.WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount)browser(view.getChildAt(i))?.let{return it}
            return null
        }
        fun evaluate(script: String): String? {
            val latch=java.util.concurrent.CountDownLatch(1);var result:String?=null
            instrumentation.runOnMainSync { browser(activity.findViewById(android.R.id.content))!!.evaluateJavascript(script){result=it;latch.countDown()} }
            assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));return result
        }
        try {
            val deadline=System.currentTimeMillis()+30000
            while(System.currentTimeMillis()<deadline && evaluate("document.readyState==='complete'&&!!document.querySelector('.fluent-window')")!="true")android.os.SystemClock.sleep(300)
            assertTrue(activity.isTrustedPage)
            assertEquals("true",evaluate("(function(){var d=document.createElement('dialog');d.id='back-test';d.innerHTML='<button>Close</button>';document.body.appendChild(d);d.showModal();return d.open;})()"))
            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            val end=System.currentTimeMillis()+5000
            while(System.currentTimeMillis()<end && evaluate("!document.querySelector('#back-test').open")!="true")android.os.SystemClock.sleep(100)
            assertEquals("true",evaluate("!document.querySelector('#back-test').open"))
        } finally { instrumentation.runOnMainSync{activity.finish()};instrumentation.waitForIdleSync() }
    }

    @Test fun paymentScreenIsRegisteredPrivateAndLaunches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = android.content.ComponentName(context, PaymentActivity::class.java)
        val info = context.packageManager.getActivityInfo(component, 0)
        assertFalse(info.exported)
        assertEquals(PaymentActivity::class.java.name, info.name)
        // Read-only form initialization: no card data, confirmation or transfer is submitted.
        val intent = android.content.Intent(context, PaymentActivity::class.java)
            .putExtra("paymentUrl", "https://yoomoney.ru/quickpay/confirm.xml?receiver=4100119557878170&sum=59&label=viht_form_smoke_no_transfer&quickpay-form=shop")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            assertTrue(activity is PaymentActivity)
            assertFalse(activity.isFinishing)
            fun webView(view: android.view.View): android.webkit.WebView? {
                if (view is android.webkit.WebView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
                return null
            }
            var formReady = false
            val deadline = System.currentTimeMillis() + 60000
            while (!formReady && System.currentTimeMillis() < deadline) {
                val latch = java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val browser = webView(activity.findViewById(android.R.id.content))
                    assertNotNull(browser)
                    browser!!.evaluateJavascript("""
                        (function(){var t=document.body?document.body.innerText:'';
                        var count=Array.from(document.querySelectorAll('input')).filter(function(e){return e.type!=='hidden'&&e.getBoundingClientRect().width>0;}).length;
                        return count>=3&&/номер\s+карты|card\s+number/i.test(t)&&/59/.test(t);})()
                    """.trimIndent()) { value -> formReady = value == "true"; latch.countDown() }
                }
                assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
                if (!formReady) android.os.SystemClock.sleep(700)
            }
            assertTrue("YooMoney must render amount and card inputs, not just an empty window", formReady)

        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

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
    @Test fun quickTileLongPressResolvesToMainActivity() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=android.content.Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").setPackage(context.packageName)
        assertEquals(MainActivity::class.java.name,context.packageManager.resolveActivity(intent,0)?.activityInfo?.name)
    }
    @Test fun backgroundServiceAndQuickTileAreDeclaredCorrectly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = context.packageManager.getServiceInfo(android.content.ComponentName(context, AutoVlessVpnService::class.java), 0)
        assertEquals(0, service.flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK)
        val tile = context.packageManager.getServiceInfo(android.content.ComponentName(context, VihtTileService::class.java), 0)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.permission)
        assertTrue(tile.exported)
        VpnRunStore.start(context, "fixture-only")
        assertTrue(VpnRunStore.desired(context))
        VpnRunStore.stop(context)
        assertFalse(VpnRunStore.desired(context))
        assertNull(VpnRunStore.restore(context))
    }
    @Test fun decryptsBrowserReturnOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val vector = context.assets.open("browser-auth-vector.json").bufferedReader().use { JSONObject(it.readText()) }
        fun decode(name: String) = android.util.Base64.decode(vector.getString(name), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        val privateKey = android.util.Base64.decode(vector.getString("privateKey"), android.util.Base64.DEFAULT)
        val result = JSONObject(BrowserAuthCrypto.decrypt(privateKey, decode("key"), decode("iv"), decode("payload"), vector.getString("state")))
        assertEquals("fixture.access.token", result.getString("token"))
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
