package com.pingwin.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // No hardcoded connection may be started after boot. The user reconnects
            // after authentication and subscription synchronization in the app.
            UpdateScheduler.sync(context)
        }
    }
}
