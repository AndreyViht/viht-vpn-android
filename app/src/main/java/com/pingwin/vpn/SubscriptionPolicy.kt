package com.pingwin.vpn

import org.json.JSONObject
import java.time.Instant

object SubscriptionPolicy {
    fun active(profile: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        if (profile.optString("status") == "disabled" || profile.optString("display_status") == "disabled") return false
        val expiry = listOf("expires_at", "subscription_expires_at", "trial_ends_at").mapNotNull { key ->
            profile.optString(key).takeUnless { it.isBlank() || it == "null" }
        }.firstOrNull()
        val end = expiry?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return if (end != null) end > now else profile.optBoolean("subscription_active") && profile.optDouble("subscription_days_left", 0.0) > 0
    }
}
