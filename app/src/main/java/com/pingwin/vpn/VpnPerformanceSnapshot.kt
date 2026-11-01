package com.pingwin.vpn

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import io.nekohasekai.libbox.Libbox

/** Collected only when the owner explicitly exports diagnostics, never on a packet/timer. */
internal object VpnPerformanceSnapshot {
    fun capture(context: Context): String = runCatching {
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(PowerManager::class.java).currentThermalStatus.toString()
        } else "unavailable"
        listOf(
            "--- Performance snapshot (not an instantaneous CPU percentage) ---",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}",
            "Core: ${Libbox.version()}; configured TUN stack: gvisor",
            "VPN: ${VpnStatus.state.value}; UI foreground: ${AppVisibility.isForeground()}",
            "Process CPU cumulative ms: ${Process.getElapsedCpuTime()}",
            "Process elapsed ms: ${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()}",
            "Process PSS KiB: ${memory.totalPss}; native heap allocated bytes: ${Debug.getNativeHeapAllocatedSize()}",
            "Thermal status (Android code): $thermal",
            "--- End performance snapshot ---"
        ).joinToString("\n")
    }.getOrElse { "Performance snapshot unavailable: ${it.javaClass.simpleName}" }
}
