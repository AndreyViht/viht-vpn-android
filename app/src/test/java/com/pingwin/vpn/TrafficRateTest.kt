package com.pingwin.vpn
import org.junit.Assert.*
import org.junit.Test
class TrafficRateTest {
 @Test fun actualElapsedTimeAndByteDifferencesDetermineRate(){assertEquals(0.5,TrafficRate.megabytesPerSecond(100,1048676,2000)!!,0.000001);assertEquals(4.0,TrafficRate.megabytesPerSecond(200,4194504,1000)!!,0.000001)}
 @Test fun resetUnsupportedAndZeroTimeNeverProduceFakeSpikes(){assertNull(TrafficRate.megabytesPerSecond(100,20,1000));assertNull(TrafficRate.megabytesPerSecond(-1,100,1000));assertNull(TrafficRate.megabytesPerSecond(0,100,0));assertEquals(0.0,TrafficRate.megabytesPerSecond(100,100,1000)!!,0.0)}
}
