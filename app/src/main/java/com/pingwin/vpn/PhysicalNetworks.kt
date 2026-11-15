package com.pingwin.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object PhysicalNetworks {
    fun ranked(manager: ConnectivityManager, exclude: Network? = null): List<Network> {
        val systemDefault = manager.activeNetwork
        return manager.allNetworks.mapNotNull { network ->
            if (network == exclude) return@mapNotNull null
            val caps = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val score = (if (validated) 100 else 0) +
                (if (network == systemDefault) 10 else 0) +
                (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 1 else 0)
            network to score
        }.sortedByDescending { it.second }.map { it.first }
    }
}
