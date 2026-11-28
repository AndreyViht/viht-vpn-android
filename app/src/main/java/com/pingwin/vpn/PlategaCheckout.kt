package com.pingwin.vpn
import java.net.URI

object PlategaCheckout {
    fun isEntry(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host in setOf("pay.platega.io", "payment.platega.com") && uri.userInfo == null && uri.port in setOf(-1, 443) && !uri.path.isNullOrBlank()
    }.getOrDefault(false)
}
