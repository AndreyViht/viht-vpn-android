package com.pingwin.vpn
import org.json.JSONObject

object DeviceAccessPolicy {
    fun denied(reply: JSONObject): Boolean {
        if (reply.optString("guestAccessState") in setOf("disabled", "removed", "mismatch")) return true
        val payload=reply.optJSONObject("data") ?: reply
        if (payload.optString("guest_access_state") in setOf("disabled", "removed", "mismatch")) return true
        if (payload.optJSONObject("profile")?.optString("guest_access_state") in setOf("disabled", "removed", "mismatch")) return true
        if (!reply.optBoolean("ok")) return false // Temporary API failure is not revocation.
        val data=reply.optJSONObject("data") ?: reply
        return data.has("device_allowed") && !data.optBoolean("device_allowed",true)
    }
}
