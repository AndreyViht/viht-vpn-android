package com.pingwin.vpn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebasePush {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private fun settings(context:Context)=runCatching{JSONObject(context.getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).getString("vpnSettings","{}")?:"{}")}.getOrDefault(JSONObject())
    private fun prefs(context:Context)=context.getSharedPreferences("viht_push",Context.MODE_PRIVATE)
    private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private suspend fun <T> Task<T>.value():T=suspendCancellableCoroutine{continuation->
        addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
        addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
        addOnCanceledListener{continuation.cancel()}
    }
    private fun diagnostics(context:Context){runCatching{
        val enabled=settings(context).optBoolean("sendDiagnostics",false)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        if(!enabled)FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }}
    @Synchronized fun sync(context:Context){
        val app=context.applicationContext;diagnostics(app);if(job?.isActive==true)return
        job=scope.launch {
            try{
                val enabled=settings(app).optBoolean("notifyNews",true)&&AndroidSessionStore.hasSession(app)&&NotificationManagerCompat.from(app).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
                if(!enabled){unregisterInternal(app);return@launch}
                val messaging=FirebaseMessaging.getInstance();messaging.isAutoInitEnabled=true
                val token=withTimeout(15000){messaging.token.value()}
                val account=AppAnnouncements.accountKey(AndroidSessionStore.getToken(app));if(account.isBlank())return@launch
                val binding=hash(token+":"+account)
                val storage=prefs(app)
                if(storage.getString("binding",null)==binding&&System.currentTimeMillis()-storage.getLong("registeredAt",0)<6*3600000)return@launch
                val response=AndroidCabinetApi.request(app,JSONObject().put("action","push-register").put("method","POST").put("body",JSONObject().put("fcm_token",token)))
                if(response.optBoolean("ok")&&AndroidSessionStore.hasSession(app)&&AppAnnouncements.accountKey(AndroidSessionStore.getToken(app))==account)storage.edit().putString("binding",binding).putLong("registeredAt",System.currentTimeMillis()).apply()
            }catch(_:Exception){/* Push is optional: failures never interrupt sign-in, VPN or announcement polling. */}
        }
    }
    private suspend fun unregisterInternal(context:Context){
        val storage=prefs(context)
        runCatching{FirebaseMessaging.getInstance().isAutoInitEnabled=false}
        if(storage.contains("binding")){
            if(AndroidSessionStore.hasSession(context))runCatching{withTimeout(2500){AndroidCabinetApi.request(context,JSONObject().put("action","push-unregister").put("method","POST").put("body",JSONObject()))}}
            runCatching{withTimeout(2500){FirebaseMessaging.getInstance().deleteToken().value()}}
            storage.edit().clear().commit()
        }
    }
    suspend fun unregister(context:Context){synchronized(this){job?.cancel();job=null};unregisterInternal(context.applicationContext)}
}
