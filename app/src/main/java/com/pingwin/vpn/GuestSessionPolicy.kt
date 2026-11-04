package com.pingwin.vpn

import org.json.JSONObject

/** Only the guest service's explicit protocol is evidence of withdrawal. */
object GuestSessionPolicy {
    fun state(status: Int, body: String): String {
        val data = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        if (status in 200..299) {
            val profile = data.optJSONObject("profile")
            val value = profile?.optString("guest_access_state") ?: data.optString("guest_access_state")
            return value.takeIf { it in setOf("active", "disabled") } ?: ""
        }
        if (status !in setOf(401, 403, 410)) return ""
        val value = data.optString("guest_access_state")
        return value.takeIf { it in setOf("disabled", "removed", "mismatch") && data.optString("error_code") == "guest_$it" } ?: ""
    }
}
