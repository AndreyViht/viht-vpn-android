package com.pingwin.vpn

import android.content.Context
import org.json.JSONObject

object GuestInviteApi {
    suspend fun request(context: Context, action: String, raw: String): JSONObject {
        require(action in setOf("preview","accept"))
        val token=if(raw.startsWith("https://anviht.ru/join#"))raw.substringAfter('#') else raw.trim()
        require(Regex("(?:[a-f0-9]{64}|[0-9]{6})").matches(token)){"Это не приглашение Viht VPN"}
        val guest=if(action=="accept")AndroidSessionStore.guestAttempt(context,token) else null
        val body=JSONObject().put("action",action).put("invite",token).apply{if(guest!=null)put("guest_token",guest)}
        val reply=VihtHttp.request("https://anviht.ru/api/device-invite","POST",mapOf("Content-Type" to "application/json","x-hwid" to VihtPreferences.getHwid(context),"x-device-model" to VihtPreferences.getDeviceModel(),"x-app-name" to "Viht Android","x-app-version" to BuildConfig.VERSION_NAME),body.toString())
        val result=runCatching{JSONObject(reply.body)}.getOrNull()?:error("Сервис приглашений не ответил")
        if(reply.status !in 200..299 || !result.optBoolean("ok")){if(action=="accept"&&reply.status in 400..499)AndroidSessionStore.clearPendingGuest(context);error(result.optString("error","Не удалось подключиться"))}
        if(guest!=null){AndroidSessionStore.acceptGuest(context,guest);result.put("token",guest)}
        return result
    }
}
