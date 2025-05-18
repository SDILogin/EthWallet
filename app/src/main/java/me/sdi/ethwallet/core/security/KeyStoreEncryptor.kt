package me.sdi.ethwallet.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import me.sdi.ethwallet.Logger
import java.math.BigInteger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides encryption and decryption services using Android KeyStore.
 * Uses AES/GCM/NoPadding for secure encryption.
 */
class KeyStoreEncryptor {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val ETHEREUM_KEY_ALIAS_PREFIX = "eth_wallet_key_"
    }

    /**
     * Encrypts a private key and returns the result as a Base64 string.
     *
     * @param privateKey The Ethereum wallet private key to encrypt
     * @param address The associated Ethereum address (used to create a unique key alias)
     * @return Base64 encoded string of the encrypted private key
     */
    fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
        try {
            val keyAlias = generateKeyAlias(address)
            Logger.debug("KeyStoreEncryptor") {
                "Encrypting private key for address $address with alias $keyAlias"
            }

            // Get or create the encryption key in the KeyStore
            val secretKey = getOrCreateSecretKey(keyAlias)

            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Get the initialization vector
            val iv = cipher.iv

            // Convert BigInteger to byte array and encrypt
            val privateKeyBytes = privateKey.toByteArray()
            val encryptedBytes = cipher.doFinal(privateKeyBytes)

            // Combine IV and encrypted bytes (IV needs to be stored for decryption)
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            // Convert to Base64 for storage
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.debug("KeyStoreEncryptor") {
                "Error encrypting private key: ${e.message}"
            }
            throw SecurityException("Failed to encrypt private key: ${e.message}", e)
        }
    }

    /**
     * Decrypts a private key from a Base64 encoded string.
     *
     * @param encryptedBase64 The Base64 encoded encrypted private key
     * @param address The associated Ethereum address
     * @return The decrypted private key as a BigInteger
     */
    fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
        try {
            val keyAlias = generateKeyAlias(address)
            Logger.debug("KeyStoreEncryptor") {
                "Decrypting private key for address $address with alias $keyAlias"
            }

            // Get the encryption key from the KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey =
                keyStore.getKey(keyAlias, null) as? SecretKey
                    ?: throw SecurityException("No key found with alias: $keyAlias")

            // Decode the Base64 string
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)

            // Extract IV and encrypted data
            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            // Decrypt the data
            val decryptedBytes = cipher.doFinal(encrypted)

            // Convert back to BigInteger
            return BigInteger(decryptedBytes)
        } catch (e: Exception) {
            Logger.debug("KeyStoreEncryptor") {
                "Error decrypting private key: ${e.message}"
            }
            throw SecurityException("Failed to decrypt private key: ${e.message}", e)
        }
    }

    /**
     * Generates a consistent key alias for a given address.
     */
    private fun generateKeyAlias(address: String): String {
        return "$ETHEREUM_KEY_ALIAS_PREFIX${address.lowercase()}"
    }

    /**
     * Gets an existing key from the KeyStore or creates a new one if it doesn't exist.
     *
     * @param keyAlias The alias for the key
     * @return The SecretKey for encryption/decryption
     */
    private fun getOrCreateSecretKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if key already exists
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

        // Create new key if not exists
        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        val keyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false) // Set to true for extra security with user auth
                .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
