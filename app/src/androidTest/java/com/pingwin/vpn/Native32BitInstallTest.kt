package com.pingwin.vpn

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.libbox.Libbox
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs in a genuinely 32-bit emulator process, after PackageInstaller accepts the APK. */
@RunWith(AndroidJUnit4::class)
class Native32BitInstallTest {
    @Test fun installed32BitPackageLoadsItsVpnLibraries() {
        assertFalse("This check must execute in a 32-bit process", Process.is64Bit())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("32-bit libbox must load and answer JNI calls", Libbox.version().isNotBlank())
        System.loadLibrary("hev-socks5-tunnel")
        val source = VihtVpnConfig.build(ConnectionProfileParser.parse(
            "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com&type=ws&path=%2Fvpn"
        ), JSONObject())
        assertFalse("32-bit devices must select the packaged sing-box engine", XrayHevRuntime.available(context, source))
        val localConfig = """{"inbounds":[{"type":"socks","tag":"local","listen":"127.0.0.1","listen_port":31801}],"outbounds":[{"type":"direct","tag":"direct"}],"route":{"final":"direct"}}"""
        LibboxValidator.validate(localConfig).getOrThrow()
    }
}
