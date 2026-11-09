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
        // MAX uses the physical network for all its connections, including calls.
        val directPackages = PaymentNetworkPolicy.directPackages(context.packageName, org.json.JSONObject(raw)).toSet() + "ru.oneme.app"
        for (pkg in directPackages) {
            try { builder.addDisallowedApplication(pkg) } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* uninstalled application */ }
        }
    }
}
