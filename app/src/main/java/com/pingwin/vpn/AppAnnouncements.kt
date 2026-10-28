package com.pingwin.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AppAnnouncements {
    const val EXTRA_OPEN = "viht_announcement_id"
    private const val CHANNEL = "viht_announcements"
    private const val NOTIFICATION = 1907
    private val lock=Mutex()
    private var lastConnectedCheck=0L
    private fun prefs(context: Context)=context.getSharedPreferences("viht_announcements",Context.MODE_PRIVATE)
    private fun enabled(context: Context)=runCatching { JSONObject(context.getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).getString("vpnSettings","{}") ?: "{}").optBoolean("notifyNews",true) }.getOrDefault(true)
    private fun owner(token:String)=runCatching { JSONObject(String(android.util.Base64.decode(token.split('.')[1],android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),Charsets.UTF_8)).getString("sub") }.getOrDefault("")
    private fun key(owner:String,id:String)=owner+":"+id
    fun cancelNotification(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION) }
    fun schedule(context: Context) {
        val manager=WorkManager.getInstance(context)
        if(!enabled(context)){manager.cancelUniqueWork("viht-announcements");return}
        val request=PeriodicWorkRequestBuilder<AnnouncementWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork("viht-announcements",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    suspend fun whileConnected(context: Context) {
        val now=System.currentTimeMillis()
        if(now-lastConnectedCheck<60000)return
        lastConnectedCheck=now
        refresh(context,true)
    }
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, notify: Boolean): JSONObject=lock.withLock {
        if(!AndroidSessionStore.hasSession(context))return@withLock JSONObject().put("ok",true).put("announcements",JSONArray())
        val token=try{AndroidSessionStore.getToken(context)}catch(_:Exception){return@withLock JSONObject().put("ok",false)}
        val account=owner(token)
        if(account.isBlank())return@withLock JSONObject().put("ok",false)
        val reply=AndroidCabinetApi.request(context,JSONObject().put("action","app-announcements"))
        if(!reply.optBoolean("ok"))return@withLock reply
        val current=runCatching { owner(AndroidSessionStore.getToken(context)) }.getOrDefault("")
        if(current!=account)return@withLock JSONObject().put("ok",false)
        val items=reply.optJSONObject("data")?.optJSONArray("announcements") ?: JSONArray()
        val storage=prefs(context);val fresh=mutableListOf<JSONObject>()
        for(i in 0 until items.length()) {
            val item=items.getJSONObject(i);val id=item.getString("id");val revision=item.getString("updated_at")
            item.put("read",storage.getString("read:"+key(account,id),null)==revision)
            if(storage.getString("delivered:"+key(account,id),null)!=revision)fresh.add(item)
        }
        val visible=AppVisibility.isForeground()
        if(fresh.isNotEmpty() && notify && !visible && enabled(context) &&
            (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val latest=fresh.first()
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"Новости Viht",NotificationManager.IMPORTANCE_DEFAULT))
            val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN,latest.getString("id"))
            val pending=PendingIntent.getActivity(context,1907,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_viht_tile)
                .setContentTitle(latest.getString("title")).setContentText(latest.getString("body"))
                .setStyle(NotificationCompat.BigTextStyle().bigText(latest.getString("body")))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
            runCatching { manager.notify(NOTIFICATION,notification) }.onSuccess {
                val edit=storage.edit();fresh.forEach { edit.putString("delivered:"+key(account,it.getString("id")),it.getString("updated_at")) };edit.apply()
            }
        } else if(visible || !enabled(context)) {
            val edit=storage.edit();fresh.forEach { edit.putString("delivered:"+key(account,it.getString("id")),it.getString("updated_at")) };edit.apply()
        }
        JSONObject().put("ok",true).put("announcements",items)
    }
    suspend fun read(context:Context,id:String,revision:String):JSONObject {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))&&revision.length in 1..80)
        val account=owner(AndroidSessionStore.getToken(context))
        check(account.isNotBlank())
        prefs(context).edit().putString("read:"+key(account,id),revision).apply()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        return JSONObject().put("ok",true)
    }
}

class AnnouncementWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result=runCatching { runBlocking { AppAnnouncements.refresh(applicationContext,true) };Result.success() }.getOrElse { Result.retry() }
}
