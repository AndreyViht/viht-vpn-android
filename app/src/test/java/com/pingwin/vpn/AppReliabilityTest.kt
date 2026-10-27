package com.pingwin.vpn
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReliabilityTest {
    @Test fun reconnectPreferenceIsEnabledByDefaultAndCanBeDisabledNatively() {
        assertTrue(VpnRecoveryPolicy.enabled(JSONObject()))
        assertFalse(VpnRecoveryPolicy.enabled(JSONObject().put("autoReconnect",false)))
        assertTrue(VpnRecoveryPolicy.enabled(JSONObject().put("autoReconnect",true)))
    }
    @Test fun explicitDeviceRevocationIsReadInsideNativeResponseEnvelope() {
        assertTrue(DeviceAccessPolicy.denied(JSONObject().put("ok",true).put("data",JSONObject().put("device_allowed",false))))
        assertFalse(DeviceAccessPolicy.denied(JSONObject().put("ok",true).put("data",JSONObject().put("device_allowed",true))))
        assertFalse(DeviceAccessPolicy.denied(JSONObject().put("ok",false).put("error","offline")))
        assertFalse(DeviceAccessPolicy.denied(JSONObject().put("ok",true).put("data",JSONObject())))
    }
    @Test fun bankSchemesAndFallbacksDoNotAllowArbitraryLocalIntents() {
        assertTrue(BankLinkPolicy.safePackage("ru.sberbankmobile"))
        assertFalse(BankLinkPolicy.safePackage("../Settings"))
        for(scheme in listOf("bank100000000111","sberpay","tinkoffbank","yoomoney"))assertTrue(BankLinkPolicy.supportedScheme(scheme))
        for(scheme in listOf("file","javascript","content","settings","intent",null))assertFalse(BankLinkPolicy.supportedScheme(scheme))
        assertTrue(BankLinkPolicy.safeFallback("https://bank.example/confirm"))
        assertFalse(BankLinkPolicy.safeFallback("javascript:alert(1)"))
        assertFalse(BankLinkPolicy.safeFallback("https://user@bank.example/confirm"))
        assertFalse(BankLinkPolicy.safeFallback("http://bank.example/confirm"))
    }
    @Test fun pendingUpdateSurvivesSerializationAndRejectsInvalidOrInstalledVersions() {
        val release=UpdateRelease("9.0.0-test","https://anviht.ru/download/VihtVPN-Android-9.0.0-test.apk","https://help.anviht.ru/android","Changes","a".repeat(64),1234567)
        assertEquals(release,UpdateReleaseCache.decode(UpdateReleaseCache.encode(release),"1.4.12-test"))
        assertNull(UpdateReleaseCache.decode(UpdateReleaseCache.encode(release),"9.0.0-test"))
        assertNull(UpdateReleaseCache.decode("broken","1.0.0"))
        assertFalse(UpdateReleaseCache.trusted(release.copy(apkUrl="https://evil.example/update.apk")))
        assertFalse(UpdateReleaseCache.trusted(release.copy(apkSha256=null)))
        assertFalse(UpdateReleaseCache.trusted(release.copy(apkUrl="https://anviht.ru/download/../evil.apk")))
    }
}
