package com.pingwin.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionPolicyTest {
    @Test fun validatesExpiryAndDisabledAccounts() {
        val profile = JSONObject().put("expires_at", "2030-01-01T00:00:00Z")
        assertTrue(SubscriptionPolicy.active(profile, 1000))
        assertFalse(SubscriptionPolicy.active(profile.put("status", "disabled"), 1000))
        assertFalse(SubscriptionPolicy.active(JSONObject().put("expires_at", "2000-01-01T00:00:00Z")))
        assertFalse(SubscriptionPolicy.active(JSONObject()))
        assertTrue(SubscriptionPolicy.active(JSONObject().put("subscription_active", true).put("subscription_days_left", 2)))
    }
}
