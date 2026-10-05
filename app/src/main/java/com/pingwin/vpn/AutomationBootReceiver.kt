package com.pingwin.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AutomationService.sync(context)
                if (VihtPreferences.isAutoConnectOnBoot(context)) {
                    runCatching {
                        val serverId = VihtPreferences.getSelectedServerId(context)
                        val server = VihtApiClient.DEFAULT_SERVERS.find { it.id == serverId }
                            ?: VihtApiClient.DEFAULT_SERVERS.first()
                        if (server.vlessUri.isNotBlank()) {
                            val profile = ConnectionProfileParser.parse(server.vlessUri)
                            val routing = RoutingSettingsStore.load(context).copy(
                                bypassRussiaSites = VihtPreferences.isBypassRussianSites(context)
                            )
                            val config = ConnectionConfigBuilder.build(profile, routing)
                            AutoVlessVpnService.start(context, config, server.id)
                        }
                    }
                }
            }
        }
    }
}
