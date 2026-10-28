package com.pingwin.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class VihtTileService : TileService() {
    private var scope: CoroutineScope? = null
    override fun onStartListening() {
        super.onStartListening()
        getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).edit().putBoolean("tileAdded",true).apply()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { owner -> owner.launch { VpnStatus.state.collect { refresh() } } }
        refresh()
    }
    override fun onTileAdded() { super.onTileAdded(); getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).edit().putBoolean("tileAdded",true).apply() }
    override fun onTileRemoved() { getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).edit().putBoolean("tileAdded",false).apply(); super.onTileRemoved() }
    override fun onStopListening() { scope?.cancel(); scope = null; super.onStopListening() }
    override fun onDestroy() { scope?.cancel(); super.onDestroy() }
    private fun refresh() {
        val current = VpnStatus.state.value
        qsTile?.apply {
            label = "Viht"
            state = if (current == VpnConnectionState.CONNECTED || current == VpnConnectionState.CONNECTING) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = when(current) { VpnConnectionState.CONNECTED -> "Подключено"; VpnConnectionState.CONNECTING -> "Подключение…"; VpnConnectionState.ERROR -> "Ошибка — откройте Viht"; else -> "Отключено" }
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) { unlockAndRun { toggle() }; return }
        toggle()
    }
    private fun toggle() {
        if (VpnStatus.state.value in setOf(VpnConnectionState.CONNECTED, VpnConnectionState.CONNECTING)) {
            AutoVlessVpnService.stop(this); refresh(); return
        }
        if (android.net.VpnService.prepare(this) == null && AndroidSessionStore.hasSession(this) && ConnectionStore.selected(this) != null) {
            VpnStatus.set(VpnConnectionState.CONNECTING)
            try { AutoVlessVpnService.startFromTile(this) }
            catch (failure: Exception) {
                DiagnosticLogStore.append(this,"Плитка VPN: " + (failure.message ?: "запуск запрещён"))
                VpnStatus.fail("Android не разрешил запустить VPN. Откройте приложение и повторите подключение.", false)
            }
            refresh()
            return
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("viht_tile_connect", true)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else openLegacy(intent)
    }
    // API 24–33 only: the PendingIntent overload does not exist on these versions.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openLegacy(intent: Intent) {
        check(Build.VERSION.SDK_INT < 34)
        startActivityAndCollapse(intent)
    }
    companion object {
        fun refresh(context: Context) { runCatching { requestListeningState(context, ComponentName(context, VihtTileService::class.java)) } }
    }
}
