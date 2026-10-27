package com.pingwin.vpn

object VpnRecoveryPolicy {
    fun allowed(desired: Boolean, destroyed: Boolean, request: Int, latest: Int) = desired && !destroyed && request > 0 && request == latest
    fun delayMillis(attempt: Int): Long = longArrayOf(1000, 2000, 5000, 10000, 20000, 30000)[attempt.coerceIn(0, 5)]
    fun addressKey(bytes: ByteArray) = bytes.take(if (bytes.size == 16) 8 else bytes.size).joinToString(".") { (it.toInt() and 255).toString() }
    fun failed(failures: Int) = failures >= 2
}
