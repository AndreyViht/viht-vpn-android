package com.pingwin.vpn
import org.json.JSONObject

object DeviceAccessPolicy {
    fun denied(reply: JSONObject): Boolean {
        if (!reply.optBoolean("ok")) return false // Temporary API failure is not revocation.
        val data=reply.optJSONObject("data") ?: reply
        return data.has("device_allowed") && !data.optBoolean("device_allowed",true)
    }
}
