package com.pingwin.vpn

/** Beeline rejects datacenter exits. Keep its site and Android app on the physical network. */
object BeelineNetworkPolicy {
    val domains = listOf("beeline.ru") // Includes shop, account, API and regional subdomains.
    val packages = listOf("ru.beeline.services")
}
