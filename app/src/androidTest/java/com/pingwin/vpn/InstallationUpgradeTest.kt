package com.pingwin.vpn

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Used only by the isolated emulator fresh-install/upgrade script. */
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 33)
class InstallationUpgradeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun requireIsolatedInstallScenario() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installScenario") == "true")
    }

    private fun assertFinalVersion() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("2.0.0.24", info.versionName)
        assertTrue(info.longVersionCode > 20182L)
    }

    @Test fun finalApkInstallsAndActivityStarts() {
        assertFinalVersion()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try { assertFalse(activity.isFinishing) }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun writeOldVersionMarker() {
        assertEquals("2.0.0.22", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        assertTrue(context.getSharedPreferences("viht_install_upgrade_fixture", 0).edit()
            .putString("marker", "preserve-from-22").commit())
    }

    @Test fun upgradePreservesExistingAppData() {
        assertFinalVersion()
        val preferences = context.getSharedPreferences("viht_install_upgrade_fixture", 0)
        assertEquals("preserve-from-22", preferences.getString("marker", null))
        preferences.edit().clear().commit()
    }
}
