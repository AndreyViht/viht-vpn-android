package com.pingwin.vpn
import java.net.URI
import org.json.JSONObject

object UpdateReleaseCache {
    fun encode(release: UpdateRelease) = JSONObject().put("version",release.version).put("url",release.apkUrl)
        .put("page",release.releaseUrl).put("notes",release.releaseNotes).put("sha",release.apkSha256).put("size",release.apkSizeBytes).toString()
    fun trusted(release: UpdateRelease): Boolean = runCatching {
        val uri=URI(release.apkUrl)
        uri.scheme=="https" && uri.userInfo==null && uri.port in setOf(-1,443) &&
            uri.normalize().path==uri.path && release.apkSizeBytes>0 && release.apkSha256?.matches(Regex("[a-fA-F0-9]{64}"))==true &&
            ((uri.host=="anviht.ru" && uri.path.matches(Regex("/download/VihtVPN-Android-[A-Za-z0-9.-]+\\.apk"))) ||
                (uri.host=="github.com" && uri.path.startsWith("/AndreyViht/viht-vpn-android/releases/download/")))
    }.getOrDefault(false)
    fun decode(value: String?, current: String): UpdateRelease? = runCatching {
        if(value==null)return@runCatching null
        val json=JSONObject(value)
        UpdateRelease(json.getString("version"),json.getString("url"),json.optString("page"),json.optString("notes"),json.getString("sha"),json.getLong("size"))
            .takeIf { trusted(it) && UpdateChecker.isNewerVersion(it.version,current) }
    }.getOrNull()
}
