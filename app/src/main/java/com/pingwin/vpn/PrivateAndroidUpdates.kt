package com.pingwin.vpn

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

internal object PrivateAndroidUpdates {
    private const val Manifest = "https://anviht.ru/api/android-private-update/manifest.json"
    private val apkPath = Regex("/api/android-private-update/VihtVPN-Android-[A-Za-z0-9.-]+[.]apk")

    fun isApkUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "anviht.ru" && uri.port in setOf(-1, 443) &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            apkPath.matches(uri.rawPath)
    }.getOrDefault(false)

    suspend fun headers(context: Context): Map<String, String> {
        check(BuildConfig.VERSION_NAME.endsWith(".test"))
        val token = AndroidSessionStore.getToken(context)
        check(token.isNotBlank() && !token.startsWith("vg_")) { "Войдите в аккаунт владельца" }
        return mapOf("Authorization" to "Bearer " + token, "X-Viht-Device" to VihtPreferences.getHwid(context))
    }

    suspend fun latest(context: Context): UpdateRelease? {
        if (!BuildConfig.VERSION_NAME.endsWith(".test")) return null
        return try {
            val credentials = headers(context)
            withContext(Dispatchers.IO) {
                UpdateChecker.fetchRelease(Manifest, credentials).takeIf {
                    isApkUrl(it.apkUrl) && UpdateReleaseCache.trusted(it)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
