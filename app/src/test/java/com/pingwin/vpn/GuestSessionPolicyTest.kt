package com.pingwin.vpn
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class GuestSessionPolicyTest {
 @Test fun temporaryErrorsPreserveGuestLogin(){
  for(status in listOf(401,403,500,503))assertEquals("",GuestSessionPolicy.state(status,"<html>unavailable</html>"))
  assertEquals("",GuestSessionPolicy.state(401,"{\"error\":\"Unauthorized\"}"))
  assertEquals("",GuestSessionPolicy.state(503,"{\"error_code\":\"guest_removed\",\"guest_access_state\":\"removed\"}"))
 }
 @Test fun explicitRemovalAndDisableAreDistinct(){
  assertEquals("removed",GuestSessionPolicy.state(401,"{\"error_code\":\"guest_removed\",\"guest_access_state\":\"removed\"}"))
  assertEquals("disabled",GuestSessionPolicy.state(200,"{\"profile\":{\"guest_access_state\":\"disabled\"}}"))
  assertEquals("active",GuestSessionPolicy.state(200,"{\"profile\":{\"guest_access_state\":\"active\"}}"))
 }
 @Test fun revokedGuestCannotConnect(){
  assertTrue(DeviceAccessPolicy.denied(JSONObject("{\"ok\":false,\"guestAccessState\":\"removed\"}")))
  assertTrue(DeviceAccessPolicy.denied(JSONObject("{\"ok\":true,\"data\":{\"profile\":{\"guest_access_state\":\"disabled\"}}}")))
  assertFalse(DeviceAccessPolicy.denied(JSONObject("{\"ok\":false,\"error\":\"Temporary error\"}")))
 }
}
