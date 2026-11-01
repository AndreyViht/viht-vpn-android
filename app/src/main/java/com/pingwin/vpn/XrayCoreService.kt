package com.pingwin.vpn

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/** Bound-only private process: Android tears it down when its VPN owner unbinds or dies. */
class XrayCoreService : Service() {
    override fun onCreate() { super.onCreate(); go.Seq.setContext(applicationContext) }
    private var core: CoreController? = null
    private val callback = object : CoreCallbackHandler {
        override fun startup() = 0L
        override fun shutdown() = 0L
        override fun onEmitStatus(code: Long, message: String?) = 0L
    }
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in 1..3) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ru.anviht.xray")
            try {
                when (code) {
                    1 -> {
                        core?.stopLoop()
                        Libv2ray.initCoreEnv(filesDir.absolutePath, "")
                        core = Libv2ray.newCoreController(callback).also { it.startLoop(data.readString()!!, 0) }
                        check(core?.isRunning == true) { "Xray не запустился" }
                    }
                    2 -> { core?.stopLoop(); core = null }
                }
                reply?.writeNoException()
                if (code == 3) reply?.writeString("${Libv2ray.checkVersionX()}; PSS KiB=${android.os.Debug.getPss()}; CPU ms=${android.os.Process.getElapsedCpuTime()}")
            } catch (failure: Exception) { reply?.writeException(IllegalStateException("Ошибка нового ядра Xray: ${failure.javaClass.simpleName}")) }
            return true
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder`r`n    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() { runCatching { core?.stopLoop() }; core = null; super.onDestroy() }
}
