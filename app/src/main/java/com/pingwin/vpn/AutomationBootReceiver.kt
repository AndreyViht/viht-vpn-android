package com.pingwin.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateScheduler.sync(context)
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) runCatching { AutoVlessVpnService.resumeAfterUpdate(context) }
        }
    }
}
