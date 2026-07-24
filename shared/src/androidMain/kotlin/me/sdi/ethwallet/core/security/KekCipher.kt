package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Software AES-256-GCM layer used to wrap a secret under an in-memory key.
 *
 * Blobs are shaped `iv(12) ‖ ciphertext+tag`; a fresh [SecureRandom] IV is prepended on every
 * [encrypt]. Callers own threading — these functions run synchronously on the caller's thread.
 */
object KekCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128


    suspend fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val iv = ByteArray(GCM_IV_LENGTH).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        iv + cipher.doFinal(plaintext)
    }

    suspend fun decrypt(key: ByteArray, blob: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        require(blob.size >= GCM_IV_LENGTH) { "KEK blob is shorter than the IV" }
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.doFinal(ciphertext)
    }
}
