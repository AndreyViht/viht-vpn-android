package com.pingwin.vpn
import java.net.URI

object PlategaCheckout {
    fun isEntry(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "pay.platega.io" && uri.userInfo == null && uri.port in setOf(-1, 443) && !uri.path.isNullOrBlank()
    }.getOrDefault(false)
}
