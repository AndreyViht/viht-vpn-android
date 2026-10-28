package com.pingwin.vpn

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class AndroidUpdateBridge(private val activity: MainActivity, private val progress: (JSONObject) -> Unit) {
    private val downloadLock=Mutex()
    private val preferences=activity.getSharedPreferences("viht_update_release",Context.MODE_PRIVATE)
    @Volatile private var available: UpdateRelease? = UpdateReleaseCache.decode(preferences.getString("available",null),BuildConfig.VERSION_NAME)
    private var verifiedStamp: String? = null
    private var verifiedResult = false
    private fun describe(release: UpdateRelease) = JSONObject().put("version", release.version).put("fileName", release.apkUrl.substringAfterLast('/'))
        .put("downloadUrl", release.apkUrl).put("sizeBytes", release.apkSizeBytes).put("sizeFormatted", "%.1f МБ".format(release.apkSizeBytes / 1048576.0))
        .put("changelog", org.json.JSONArray(release.releaseNotes.lines().filter(String::isNotBlank).take(30)))
    fun check(): JSONObject {
        val release = UpdateChecker.getLatestRelease()
        val newer = UpdateChecker.isNewerVersion(release.version, BuildConfig.VERSION_NAME)
        if(newer) kotlin.check(UpdateReleaseCache.trusted(release)) { "Недопустимые метаданные обновления" }
        available = if (newer) release else null
        kotlin.check(preferences.edit().apply { if(newer)putString("available",UpdateReleaseCache.encode(release)) else remove("available") }.commit()) { "Не удалось сохранить обновление" }
        return JSONObject().put("ok", true).put("hasUpdate", newer).put("currentVersion", BuildConfig.VERSION_NAME).put("latestVersion", release.version).put("releaseInfo", describe(release))
    }
    private fun downloadedState(release: UpdateRelease?) = DirectUpdateDownload.state(activity,release) ?: UpdateInstaller.getDownloadState(activity)
    @Synchronized fun status(): JSONObject {
        val release=available
        val state=downloadedState(release)
        val matches=release!=null && state!=null && UpdateInstaller.getExpectedSha256(activity).equals(release.apkSha256,ignoreCase=true)
        val complete=matches && state?.status==DownloadManager.STATUS_SUCCESSFUL
        val file=state?.apkFile
        val stamp=if(complete && file!=null) "${file.absolutePath}:${file.length()}:${file.lastModified()}:${release?.apkSha256}" else null
        if(stamp!=verifiedStamp){verifiedStamp=stamp;verifiedResult=complete && file!=null && UpdateInstaller.verifyDownloadedApk(activity,file)}
        val ready=complete && verifiedResult
        val downloading=matches && state?.status in setOf(DownloadManager.STATUS_PENDING,DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PAUSED)
        val error=when { matches && state?.status==DownloadManager.STATUS_FAILED -> "Загрузка прервалась. Нажмите «Повторить загрузку»."
            matches && state?.status==DownloadManager.STATUS_PAUSED -> "Загрузка приостановлена. Нажмите «Повторить загрузку» для продолжения."
            complete && !ready -> "APK не прошёл проверку подписи, версии или контрольной суммы"
            else -> null }
        val snapshot=JSONObject().put("percent",state?.progress ?: 0).put("downloadedMb","%.1f".format((state?.downloadedBytes ?: 0).coerceAtLeast(0)/1048576.0))
            .put("totalMb","%.1f".format((state?.totalBytes ?: release?.apkSizeBytes ?: 0).coerceAtLeast(0)/1048576.0)).put("speedMb","0.0")
        return JSONObject().put("available",release?.let(::describe) ?: JSONObject.NULL).put("downloadedVersion",if(ready)release?.version else JSONObject.NULL)
            .put("downloading",downloading).put("progress",snapshot).put("error",error ?: JSONObject.NULL)
    }
    suspend fun download(request: JSONObject): JSONObject = downloadLock.withLock {
        if(available?.version!=request.optString("version"))check()
        val release=available ?: error("Доступного обновления нет")
        kotlin.check(release.version==request.optString("version")) { "Версия обновления изменилась. Повторите проверку." }
        kotlin.check(UpdateReleaseCache.trusted(release)) { "Недопустимый источник обновления" }
        val existing=status()
        if(existing.optString("downloadedVersion")==release.version)return@withLock JSONObject().put("ok",true).put("ready",true)
        if(existing.optBoolean("downloading")&&!request.optBoolean("restart"))return@withLock JSONObject().put("ok",true).put("downloading",true)
        DirectUpdateDownload.start(activity,release,request.optBoolean("restart"))
        verifiedStamp=null
        return@withLock JSONObject().put("ok",true).put("downloading",true)
    }
    suspend fun install(request: JSONObject): JSONObject = downloadLock.withLock {
        val release=available ?: error("Сначала проверьте обновления")
        kotlin.check(release.version==request.optString("version")) { "Версия обновления изменилась" }
        val state=downloadedState(release) ?: error("Сначала скачайте обновление")
        kotlin.check(state.status==DownloadManager.STATUS_SUCCESSFUL) { "Загрузка ещё не завершена" }
        kotlin.check(UpdateInstaller.getExpectedSha256(activity).equals(release.apkSha256,ignoreCase=true)) { "Скачана другая версия обновления" }
        val apk=state.apkFile ?: error("Загрузка ещё не завершена")
        kotlin.check(UpdateInstaller.verifyDownloadedApk(activity,apk)) { "APK не прошёл проверку" }
        if(!UpdateInstaller.canInstallPackages(activity)) {
            withContext(Dispatchers.Main){UpdateInstaller.openInstallPermission(activity)}
            return@withLock JSONObject().put("ok",false).put("needsPermission",true).put("error","Разрешите установку приложений, затем нажмите «Установить» ещё раз")
        }
        withContext(Dispatchers.Main){UpdateInstaller.installApk(activity,apk)}
        return@withLock JSONObject().put("ok",true)
    }
}
