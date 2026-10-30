package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class SessionRefreshPolicyTest {
    @Test fun transportOrTemporaryErrorsCannotEraseSession(){
        for(status in listOf(400,401,429,500,502,503)){
            assertFalse(SessionRefreshPolicy.revoked(status,"<html>gateway unavailable</html>"))
            assertFalse(SessionRefreshPolicy.revoked(status,"{\"code\":\"request_timeout\"}"))
        }
        assertFalse(SessionRefreshPolicy.revoked(400,"{\"error\":\"invalid_request\"}"))
    }
    @Test fun explicitRefreshRevocationRequiresSignIn(){
        for(code in listOf("refresh_token_not_found","refresh_token_already_used","session_not_found","session_expired","user_banned")){
            assertTrue(SessionRefreshPolicy.revoked(400,"{\"error_code\":\"$code\"}"))
            assertTrue(SessionRefreshPolicy.revoked(401,"{\"code\":\"$code\"}"))
            assertFalse(SessionRefreshPolicy.revoked(503,"{\"code\":\"$code\"}"))
        }
    }
}
