package com.pingwin.vpn

import android.content.Context
import android.net.VpnService

object AppRoutingConfigurator {

    fun apply(
        context: Context,
        builder: VpnService.Builder,
        routing: RoutingSettings
    ) {
        val raw=context.getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).getString("vpnSettings","{}") ?: "{}"
        for(pkg in VpnBypassSettings.packages(org.json.JSONObject(raw))) {
            if(pkg==context.packageName) continue
            try { builder.addDisallowedApplication(pkg) } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* uninstalled application */ }
        }
    }
}
