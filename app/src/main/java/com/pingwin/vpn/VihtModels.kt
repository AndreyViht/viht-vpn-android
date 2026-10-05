package com.pingwin.vpn

data class VihtServer(
    val id: String,
    val name: String,
    val flag: String,
    val host: String,
    val port: Int = 443,
    val ping: Int? = null,
    val vlessUri: String = "",
    val isYouTubeNoAds: Boolean = false,
    val isLte: Boolean = false,
    val isRecommended: Boolean = false,
    val panelName: String = "",
    val inboundId: Int = 0
)

data class VihtUserProfile(
    val userId: String = "",
    val email: String? = null,
    val telegramId: String? = null,
    val isActive: Boolean = false,
    val isTrial: Boolean = false,
    val daysRemaining: Int = 0,
    val expiresAt: String = "",
    val maxDevices: Int = 3,
    val activeDevicesCount: Int = 1,
    val token: String? = null
)

data class VihtDevice(
    val id: String,
    val model: String,
    val os: String,
    val hwid: String,
    val ip: String? = null,
    val lastActive: String = "",
    val isCurrent: Boolean = false
)

enum class VihtAuthType {
    TELEGRAM,
    VK,
    YANDEX,
    TOKEN
}
