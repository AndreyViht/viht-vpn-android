package com.pingwin.vpn

import android.app.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AndroidUpdateBridge(private val activity: MainActivity, private val progress: (JSONObject) -> Unit) {
    private var available: UpdateRelease? = null
    private fun describe(release: UpdateRelease) = JSONObject().put("version", release.version).put("fileName", release.apkUrl.substringAfterLast('/'))
        .put("downloadUrl", release.apkUrl).put("sizeBytes", release.apkSizeBytes).put("sizeFormatted", "%.1f МБ".format(release.apkSizeBytes / 1048576.0))
        .put("changelog", org.json.JSONArray(release.releaseNotes.lines().filter(String::isNotBlank).take(30)))
    fun check(): JSONObject {
        val release = UpdateChecker.getLatestRelease()
        val newer = UpdateChecker.isNewerVersion(release.version, BuildConfig.VERSION_NAME)
        available = if (newer) release else null
        return JSONObject().put("ok", true).put("hasUpdate", newer).put("currentVersion", BuildConfig.VERSION_NAME).put("latestVersion", release.version).put("releaseInfo", describe(release))
    }
    fun status(): JSONObject {
        val release = available
        val state = UpdateInstaller.getDownloadState(activity)
        val ready = state?.status == DownloadManager.STATUS_SUCCESSFUL && state.apkFile?.let { UpdateInstaller.verifyDownloadedApk(activity, it) } == true
        return JSONObject().put("available", release?.let(::describe) ?: JSONObject.NULL).put("downloadedVersion", if (ready) release?.version else JSONObject.NULL)
    }
    suspend fun download(request: JSONObject): JSONObject {
        check()
        val release = available ?: error("Доступного обновления нет")
        kotlin.check(release.version == request.optString("version")) { "Версия обновления изменилась. Повторите проверку." }
        kotlin.check(release.apkUrl.startsWith("https://github.com/AndreyViht/viht-vpn-android/releases/download/")) { "Недопустимый источник обновления" }
        kotlin.check(!release.apkSha256.isNullOrBlank()) { "Сервер не предоставил контрольную сумму APK" }
        UpdateInstaller.startDownload(activity, release.apkUrl, release.apkSha256)
        return withTimeout(5 * 60_000) {
            while (true) {
                val state = UpdateInstaller.getDownloadState(activity) ?: error("Загрузка обновления отменена")
                if (state.status == DownloadManager.STATUS_FAILED) error("Android не смог скачать обновление")
                progress(JSONObject().put("percent", state.progress ?: 0).put("downloadedMb", "%.1f".format(state.downloadedBytes / 1048576.0))
                    .put("totalMb", "%.1f".format(state.totalBytes / 1048576.0)).put("speedMb", "0.0"))
                if (state.status == DownloadManager.STATUS_SUCCESSFUL) {
                    kotlin.check(state.apkFile?.let { UpdateInstaller.verifyDownloadedApk(activity, it) } == true) { "APK не прошёл проверку подписи, версии или контрольной суммы" }
                    return@withTimeout JSONObject().put("ok", true)
                }
                delay(500)
            }
            @Suppress("UNREACHABLE_CODE") JSONObject().put("ok", false)
        }
    }
    suspend fun install(request: JSONObject): JSONObject {
        val release = available ?: error("Сначала проверьте обновления")
        kotlin.check(release.version == request.optString("version")) { "Версия обновления изменилась" }
        val state = UpdateInstaller.getDownloadState(activity) ?: error("Сначала скачайте обновление")
        val apk = state.apkFile ?: error("Загрузка ещё не завершена")
        kotlin.check(UpdateInstaller.verifyDownloadedApk(activity, apk)) { "APK не прошёл проверку" }
        if (!UpdateInstaller.canInstallPackages(activity)) {
            withContext(Dispatchers.Main) { UpdateInstaller.openInstallPermission(activity) }
            return JSONObject().put("ok", false).put("needsPermission", true).put("error", "Разрешите установку приложений, затем нажмите «Установить» ещё раз")
        }
        AutoVlessVpnService.stop(activity)
        withTimeout(15000) { VpnStatus.state.first { it == VpnConnectionState.DISCONNECTED || it == VpnConnectionState.ERROR } }
        withContext(Dispatchers.Main) { UpdateInstaller.installApk(activity, apk) }
        return JSONObject().put("ok", true)
    }
}
