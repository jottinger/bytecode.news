/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.service

import com.enigmastation.streampack.core.config.StreampackProperties
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets
import org.springframework.stereotype.Component

/** Encrypts and decrypts webhook secrets for at-rest storage */
@Component
class WebhookSecretCipher(properties: StreampackProperties) {

    private val secureRandom = SecureRandom()
    private val key: SecretKey

    init {
        val jwtSecret = properties.jwt.secret
        require(jwtSecret.isNotBlank()) {
            "streampack.jwt.secret must be configured to enable GitHub webhooks"
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(jwtSecret.toByteArray())
        key = SecretKeySpec(hash, "AES")
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()
        return Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(encoded: String): String {
        val payload = Base64.getDecoder().decode(encoded)
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return plaintext.toString(Charsets.UTF_8)
    }
}
