package com.pingwin.vpn
import java.net.URI

object BankLinkPolicy {
    fun safePackage(value: String?) = value?.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) == true
    fun supportedScheme(scheme: String?) = scheme?.lowercase()?.matches(Regex("bank[0-9]{8,20}|sberpay|sberbankonline|sberbank|sbp|mirpay|tinkoffbank|tbank|alfabank|vtb|yoomoney")) == true
    fun safeFallback(value: String?) = runCatching {
        val uri=URI(value ?: "")
        uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.port in setOf(-1,443)
    }.getOrDefault(false)
}
