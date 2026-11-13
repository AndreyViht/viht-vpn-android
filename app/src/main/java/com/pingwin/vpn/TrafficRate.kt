package com.pingwin.vpn
object TrafficRate {
 fun megabytesPerSecond(previous:Long,current:Long,elapsedMs:Long):Double? {
  if(previous<0||current<previous||elapsedMs<=0)return null
  return (current-previous).toDouble()*1000.0/elapsedMs/1048576.0
 }
}
