package com.pingwin.vpn

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Independent of the activity: closing the screen must not stop presence reports. */
object DevicePresenceReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Mutex()
    private var periodic: Job? = null
    private var requestedState: Boolean? = null
    private suspend fun report(context: Context) = requests.withLock {
        if (AndroidSessionStore.hasSession(context)) {
            try {
                val sample = VpnStatus.traffic.value
                val body = JSONObject()
                val connected = VpnStatus.state.value == VpnConnectionState.CONNECTED
                val age = System.currentTimeMillis() - sample.optLong("sampledAt", 0)
                val down = sample.optDouble("speedDown", Double.NaN)
                val up = sample.optDouble("speedUp", Double.NaN)
                if (connected && age in 0..10_000 && down.isFinite() && up.isFinite()) {
                    body.put("traffic", JSONObject()
                        .put("download_bps", (down.coerceAtLeast(0.0) * 1048576).toLong())
                        .put("upload_bps", (up.coerceAtLeast(0.0) * 1048576).toLong()))
                }
                val reply=AndroidCabinetApi.request(context, JSONObject().put("action", "device-heartbeat").put("method", "POST").put("body", body))
                if (DeviceAccessPolicy.denied(reply) && VpnRunStore.desired(context)) AutoVlessVpnService.stop(context,"Устройство отключено в подписке")
                if(VpnRunStore.desired(context))AppAnnouncements.whileConnected(context)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A temporary network failure is represented by heartbeat expiry. */ }
        }
    }
    @Synchronized fun connected(context: Context) {
        requestedState = true
        periodic?.cancel()
        val app = context.applicationContext
        periodic = scope.launch {
            while (isActive && VpnStatus.state.value == VpnConnectionState.CONNECTED) { report(app); delay(15_000) }
        }
    }
    @Synchronized fun stopped(context: Context) {
        if (requestedState == false) return
        requestedState = false
        periodic?.cancel(); periodic = null
        scope.launch { report(context.applicationContext) }
    }
}
