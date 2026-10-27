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
        // Viht's isolated payment WebView and control-plane requests must use the
        // physical network. Other applications remain captured by the VPN.
        for(pkg in PaymentNetworkPolicy.directPackages(context.packageName,org.json.JSONObject(raw))) {
            try { builder.addDisallowedApplication(pkg) } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* uninstalled application */ }
        }
    }
}
