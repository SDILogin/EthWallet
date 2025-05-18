package me.sdi.ethwallet.core.security

import java.math.BigInteger

/**
 * Interface for securely encrypting and decrypting private keys using platform security features.
 */
interface PrivateKeyEncryptor {
    /**
     * Encrypts a private key using secure storage mechanisms.
     *
     * @param privateKey The private key to encrypt
     * @param keyAlias A unique identifier for this key to be used when decrypting
     * @return An encrypted representation of the private key
     */
    fun encryptPrivateKey(privateKey: BigInteger, keyAlias: String): ByteArray

    /**
     * Decrypts a previously encrypted private key.
     *
     * @param encryptedKey The encrypted private key bytes
     * @param keyAlias The unique identifier used when the key was encrypted
     * @return The decrypted private key
     * @throws SecurityException if decryption fails or the key is not found
     */
    fun decryptPrivateKey(encryptedKey: ByteArray, keyAlias: String): BigInteger
}
