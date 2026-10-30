package com.pingwin.vpn

import org.json.JSONObject

object SessionRefreshPolicy {
    private val revokedCodes=setOf("refresh_token_not_found","refresh_token_already_used","refresh_token_revoked","session_not_found","session_expired","user_not_found","user_banned")
    fun revoked(status:Int,body:String):Boolean {
        if(status!=400&&status!=401)return false
        val error=runCatching{JSONObject(body)}.getOrNull()?:return false
        return (error.optString("error_code").ifBlank{error.optString("code")}) in revokedCodes
    }
}
