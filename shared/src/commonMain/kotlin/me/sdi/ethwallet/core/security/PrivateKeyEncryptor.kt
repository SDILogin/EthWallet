package me.sdi.ethwallet.core.security

import me.sdi.ethwallet.kotlin.PrivateKey

/**
 * Interface for safely encrypting/decrypting private keys.
 */
interface PrivateKeyEncryptor {
    suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String

    suspend fun decryptPrivateKey(encryptedBase64: String, address: String): PrivateKey
}
