package com.pingwin.vpn

import android.content.Context
import org.json.JSONObject

object VpnRunStore {
    private fun prefs(context: Context) = context.getSharedPreferences("viht_vpn_run", Context.MODE_PRIVATE)
    fun desired(context: Context) = prefs(context).getBoolean("desired", false)
    fun start(context: Context, id: String) { check(prefs(context).edit().putBoolean("desired", true).putBoolean("verified",false).putString("connection", id).commit()) }
    fun verified(context: Context) = prefs(context).getBoolean("verified", false)
    fun markVerified(context: Context) { prefs(context).edit().putBoolean("verified",true).apply() }
    fun stop(context: Context) { prefs(context).edit().putBoolean("desired", false).commit() }
    fun restore(context: Context): Pair<String, String>? {
        if (!desired(context)) return null
        val id = prefs(context).getString("connection", null) ?: return null
        val selected = ConnectionStore.findById(context, id) ?: return null
        if (!AndroidSessionStore.hasSession(context)) return null
        val settings = JSONObject(context.getSharedPreferences("viht_ui_settings", Context.MODE_PRIVATE).getString("vpnSettings", "{}") ?: "{}")
        return VihtVpnConfig.build(ConnectionProfileParser.parse(selected.link), settings) to id
    }
}
