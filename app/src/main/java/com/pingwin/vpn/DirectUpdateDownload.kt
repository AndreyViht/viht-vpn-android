package com.pingwin.vpn

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/** App-owned sockets are explicitly bound to Wi-Fi/mobile, never the VPN or DownloadManager UID. */
object DirectUpdateDownload {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val generation=AtomicLong()
    private var job:Job?=null
    @Volatile private var connection:HttpURLConnection?=null
    private fun prefs(c:Context)=c.getSharedPreferences("viht_direct_update",Context.MODE_PRIVATE)
    fun state(context:Context,release:UpdateRelease?):UpdateDownloadState? {
        if(release==null)return null
        val p=prefs(context)
        if(p.getString("sha",null)!=release.apkSha256)return null
        var status=p.getInt("status",DownloadManager.STATUS_FAILED)
        if(status in setOf(DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PENDING)&&job?.isActive!=true)status=DownloadManager.STATUS_PAUSED
        val file=UpdateInstaller.getDownloadFile(context)
        val bytes=file.takeIf{it.exists()}?.length() ?: 0
        return UpdateDownloadState(status,if(release.apkSizeBytes>0)(bytes*100/release.apkSizeBytes).toInt().coerceIn(0,100)else 0,if(status==DownloadManager.STATUS_SUCCESSFUL)file else null,bytes,release.apkSizeBytes)
    }
    @Synchronized fun start(context:Context,release:UpdateRelease,restart:Boolean=false) {
        val app=context.applicationContext;val p=prefs(app)
        if(!restart&&job?.isActive==true&&p.getString("sha",null)==release.apkSha256)return
        val epoch=generation.incrementAndGet();connection?.disconnect();job?.cancel()
        val same=p.getString("sha",null)==release.apkSha256
        UpdateInstaller.prepareDirectDownload(app,release.apkSha256!!,same)
        p.edit().putString("sha",release.apkSha256).putInt("status",DownloadManager.STATUS_PENDING).putString("error","").commit()
        job=scope.launch {
            var current:HttpURLConnection?=null
            try {
                val file=UpdateInstaller.getDownloadFile(app)
                if(file.length()==release.apkSizeBytes&&UpdateInstaller.verifyDownloadedApk(app,file)){p.edit().putInt("status",DownloadManager.STATUS_SUCCESSFUL).apply();return@launch}
                if(file.length()>=release.apkSizeBytes)file.delete()
                val offset=file.takeIf{it.exists()}?.length() ?: 0L
                val manager=app.getSystemService(ConnectivityManager::class.java)
                val networks=manager.allNetworks.filter{n->manager.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true}
                val network=networks.firstOrNull{manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true} ?: networks.firstOrNull() ?: error("Нет Wi-Fi или мобильного интернета")
                ensureActive();check(generation.get()==epoch)
                val conn=network.openConnection(URL(release.apkUrl)) as HttpURLConnection;current=conn
                synchronized(DirectUpdateDownload){check(generation.get()==epoch);connection=conn}
                conn.connectTimeout=15000;conn.readTimeout=15000;conn.useCaches=false
                if(offset>0)conn.setRequestProperty("Range","bytes=$offset-")
                val code=conn.responseCode
                check(code==200||code==206){"Сервер загрузки недоступен: $code"}
                val append=offset>0&&code==206
                if(append)check(conn.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true){"Сервер не подтвердил продолжение загрузки"}
                var total=if(append)offset else 0L
                p.edit().putInt("status",DownloadManager.STATUS_RUNNING).apply()
                conn.inputStream.use{input->FileOutputStream(file,append).use{output->val buffer=ByteArray(65536);while(true){ensureActive();check(generation.get()==epoch);val count=input.read(buffer);if(count<0)break;ensureActive();check(generation.get()==epoch);total+=count;check(total<=release.apkSizeBytes){"Неверный размер APK"};output.write(buffer,0,count)}}}
                check(total==release.apkSizeBytes){"Загрузка прервана. Нажмите Повторить для продолжения"}
                check(UpdateInstaller.verifyDownloadedApk(app,file)){"APK не прошёл проверку подписи или контрольной суммы"}
                if(generation.get()==epoch)p.edit().putInt("status",DownloadManager.STATUS_SUCCESSFUL).apply()
            }catch(error:Exception){if(generation.get()==epoch)p.edit().putInt("status",DownloadManager.STATUS_FAILED).putString("error",error.message ?: "Загрузка прервана").apply()}
            finally{current?.disconnect();if(generation.get()==epoch)connection=null}
        }
    }
}
