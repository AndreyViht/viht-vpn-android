package com.pingwin.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Locale

object VpnNotification {
    const val CHANNEL = "autovless_vpn"
    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Состояние VPN",NotificationManager.IMPORTANCE_LOW).apply { description="Сервер, скорость передачи данных и отключение VPN"; setShowBadge(false) })
    }
    fun enabled(context: Context): Boolean {
        ensureChannel(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun rate(mb: Double): String = if(mb>=1) String.format(Locale.ROOT,"%.1f МБ/с",mb) else String.format(Locale.ROOT,"%.0f КБ/с",mb.coerceAtLeast(0.0)*1024)
    fun build(context: Context, text: String): Notification {
        ensureChannel(context)
        val connected=VpnStatus.state.value==VpnConnectionState.CONNECTED
        val id=VpnStatus.activeConnectionId.value
        val name=VpnStatus.activeConnectionName ?: id?.let { ConnectionStore.findById(context,it)?.name }
        val traffic=VpnStatus.traffic.value
        val details=if(connected) "↓ ${rate(traffic.optDouble("speedDown",0.0))}   ↑ ${rate(traffic.optDouble("speedUp",0.0))}" else if(VpnStatus.state.value==VpnConnectionState.CONNECTING) "Подключение к VPN…" else text
        return NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_viht_tile)
            .setContentTitle(if(!name.isNullOrBlank()) "Viht VPN · $name" else "Viht VPN")
            .setContentText(details).setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(R.drawable.ic_viht_tile,"Отключить",PendingIntent.getService(context,1,Intent(context,AutoVlessVpnService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
}
