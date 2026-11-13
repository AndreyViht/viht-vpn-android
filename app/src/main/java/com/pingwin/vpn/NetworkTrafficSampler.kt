package com.pingwin.vpn
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject

/** Physical interfaces only: never add TUN counters to outer network counters. */
object NetworkTrafficSampler {
 private var signature = ""
 private var lastRx = 0L; private var lastTx = 0L; private var lastAt = 0L
 @Synchronized fun enrich(context: Context, traffic: JSONObject): JSONObject {
  val result = JSONObject(traffic.toString()).put("displaySpeedDown",traffic.optDouble("speedDown",0.0)).put("displaySpeedUp",traffic.optDouble("speedUp",0.0)).put("networkMeasured",false)
  if(Build.VERSION.SDK_INT < 31) return result
  return runCatching {
   val manager=context.getSystemService(ConnectivityManager::class.java)
   val names=manager.allNetworks.filter { manager.getNetworkCapabilities(it)?.let { c -> c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true }.mapNotNull { manager.getLinkProperties(it)?.interfaceName }.filterNot { it.startsWith("tun") || it=="lo" }.distinct().sorted()
   if(names.isEmpty()) { lastAt=0; return result }
   val rx=names.map { TrafficStats.getRxBytes(it) };val tx=names.map { TrafficStats.getTxBytes(it) }
   if(rx.any{it<0}||tx.any{it<0})return result
   val now=SystemClock.elapsedRealtime();val key=names.joinToString();val down=rx.sum();val up=tx.sum()
   if(key==signature && lastAt>0 && now>lastAt && now-lastAt<=5000 && down>=lastRx && up>=lastTx) {
    result.put("displaySpeedDown",TrafficRate.megabytesPerSecond(lastRx,down,now-lastAt)).put("displaySpeedUp",TrafficRate.megabytesPerSecond(lastTx,up,now-lastAt)).put("networkMeasured",true)
   }
   signature=key;lastRx=down;lastTx=up;lastAt=now;result
  }.getOrDefault(result)
 }
}
