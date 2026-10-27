package com.pingwin.vpn

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object BrowserAuthCrypto {
    fun generate() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    fun decrypt(privateKey: ByteArray, wrapped: ByteArray, iv: ByteArray, payload: ByteArray, state: String): String {
        require(wrapped.size == 256 && iv.size == 12 && payload.size in 17..16384)
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsa.init(Cipher.DECRYPT_MODE, KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKey)),
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        val secret = rsa.doFinal(wrapped)
        require(secret.size == 32)
        return try {
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
            aes.updateAAD(state.toByteArray(Charsets.UTF_8))
            String(aes.doFinal(payload), Charsets.UTF_8)
        } finally { secret.fill(0) }
    }
}
