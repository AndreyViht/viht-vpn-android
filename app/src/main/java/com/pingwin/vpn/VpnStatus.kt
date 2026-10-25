package com.pingwin.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object VpnStatus {
    @Volatile var connectedAt: Long = 0L
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var retryable: Boolean = true
        private set
    private val _traffic = MutableStateFlow(org.json.JSONObject())
    val traffic: StateFlow<org.json.JSONObject> = _traffic.asStateFlow()
    fun traffic(value: org.json.JSONObject) { _traffic.value = value }
    fun markManualStop() { retryable = false }
    fun fail(message: String, canRetry: Boolean = true) {
        lastError = message; retryable = canRetry; set(VpnConnectionState.ERROR)
    }
    fun snapshot(): org.json.JSONObject = org.json.JSONObject().put("ok", true)
        .put("state", state.value.name.lowercase()).put("connectedAt", connectedAt)
        .put("error", lastError ?: org.json.JSONObject.NULL).put("retryable", retryable)
        .put("connectionId", activeConnectionId.value ?: org.json.JSONObject.NULL)

    private val _state =
        MutableStateFlow(
            VpnConnectionState.DISCONNECTED
        )

    val state: StateFlow<VpnConnectionState> =
        _state.asStateFlow()

    private val _activeConnectionId =
        MutableStateFlow<String?>(null)

    val activeConnectionId: StateFlow<String?> =
        _activeConnectionId.asStateFlow()

    fun set(
        value: VpnConnectionState
    ) {
        if (value == VpnConnectionState.CONNECTED && _state.value != value) connectedAt = System.currentTimeMillis()
        if (value == VpnConnectionState.CONNECTING) { lastError = null; retryable = true }
        if (value == VpnConnectionState.DISCONNECTED || value == VpnConnectionState.ERROR) { connectedAt = 0L; _traffic.value = org.json.JSONObject() }
        _state.value = value
    }

    fun setActiveConnectionId(
        connectionId: String?
    ) {
        _activeConnectionId.value = connectionId
    }
}
