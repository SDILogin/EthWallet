package me.sdi.ethwallet.core.security

import java.math.BigInteger

/**
 * Interface for safely encrypting/decrypting private keys.
 */
interface PrivateKeyEncryptor {
    fun encryptPrivateKey(privateKey: BigInteger, address: String): String

    fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger
}
