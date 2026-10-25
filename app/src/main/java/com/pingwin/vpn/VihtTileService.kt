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
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { owner -> owner.launch { VpnStatus.state.collect { refresh() } } }
        refresh()
    }
    override fun onStopListening() { scope?.cancel(); scope = null; super.onStopListening() }
    override fun onDestroy() { scope?.cancel(); super.onDestroy() }
    private fun refresh() {
        val current = VpnStatus.state.value
        qsTile?.apply {
            label = "Viht VPN"
            state = if (current == VpnConnectionState.CONNECTED || current == VpnConnectionState.CONNECTING) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = when(current) { VpnConnectionState.CONNECTED -> "Подключено"; VpnConnectionState.CONNECTING -> "Подключение…"; else -> "Отключено" }
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
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("viht_tile_connect", true)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
    }
    companion object {
        fun refresh(context: Context) { runCatching { requestListeningState(context, ComponentName(context, VihtTileService::class.java)) } }
    }
}
