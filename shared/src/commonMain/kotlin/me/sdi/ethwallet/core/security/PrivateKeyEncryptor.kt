package me.sdi.ethwallet.core.security

import java.math.BigInteger

/**
 * Interface for safely encrypting/decrypting private keys.
 */
interface PrivateKeyEncryptor {
    suspend fun encryptPrivateKey(privateKey: BigInteger, address: String): String

    suspend fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger
}
