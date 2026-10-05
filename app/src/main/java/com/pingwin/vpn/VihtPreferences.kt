package com.pingwin.vpn

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

object VihtPreferences {
    private const val PREFS_NAME = "viht_vpn_preferences"

    private const val KEY_HWID = "viht_client_hwid"
    private const val KEY_AUTH_TOKEN = "viht_auth_token"
    private const val KEY_TELEGRAM_ID = "viht_telegram_id"
    private const val KEY_USER_EMAIL = "viht_user_email"
    private const val KEY_BYPASS_RU = "viht_bypass_russian_sites"
    private const val KEY_AUTO_BOOT = "viht_auto_connect_boot"
    private const val KEY_KILL_SWITCH = "viht_kill_switch"
    private const val KEY_SELECTED_SERVER_ID = "viht_selected_server_id"

    fun getHwid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_HWID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        // Generate clean unique HWID
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val generated = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "an_${androidId}_${Build.MODEL.replace(" ", "_")}"
        } else {
            "an_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        }

        prefs.edit().putString(KEY_HWID, generated).apply()
        return generated
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val model = Build.MODEL ?: "Device"
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getAuthToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun setAuthToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getTelegramId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TELEGRAM_ID, null)
    }

    fun setTelegramId(context: Context, telegramId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TELEGRAM_ID, telegramId).apply()
    }

    fun getUserEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun setUserEmail(context: Context, email: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun isBypassRussianSites(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BYPASS_RU, true)
    }

    fun setBypassRussianSites(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BYPASS_RU, enabled).apply()
    }

    fun isAutoConnectOnBoot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_BOOT, false)
    }

    fun setAutoConnectOnBoot(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
    }

    fun isKillSwitch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_KILL_SWITCH, false)
    }

    fun setKillSwitch(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    fun getSelectedServerId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_SERVER_ID, "de_1") ?: "de_1"
    }

    fun setSelectedServerId(context: Context, serverId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_SERVER_ID, serverId).apply()
    }

    fun clearAuth(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_TELEGRAM_ID)
            .remove(KEY_USER_EMAIL)
            .apply()
    }
}
