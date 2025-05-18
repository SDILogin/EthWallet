package me.sdi.ethwallet.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Implementation of PrivateKeyEncryptor that uses Android KeyStore for secure encryption.
 * This implementation uses AES/GCM/NoPadding encryption with keys stored in the Android KeyStore.
 */
class AndroidKeyStoreEncryptor : PrivateKeyEncryptor {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Encrypts a private key using the Android KeyStore.
     *
     * @param privateKey The Ethereum private key to encrypt
     * @param keyAlias A unique alias to identify this key in the KeyStore
     * @return The encrypted private key with the IV prepended
     */
    override fun encryptPrivateKey(privateKey: BigInteger, keyAlias: String): ByteArray {
        // Get or create the encryption key
        val encryptionKey = getOrCreateSecretKey(keyAlias)

        // Initialize the cipher for encryption
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)

        // Get the initialization vector
        val iv = cipher.iv

        // Convert BigInteger to byte array
        val privateKeyBytes = privateKey.toByteArray()

        // Encrypt the private key
        val encryptedBytes = cipher.doFinal(privateKeyBytes)

        // Combine IV and encrypted bytes (IV needs to be stored for decryption)
        return iv + encryptedBytes
    }

    /**
     * Decrypts a previously encrypted private key.
     *
     * @param encryptedKey The encrypted private key with the IV prepended
     * @param keyAlias The alias used to encrypt the key
     * @return The decrypted private key as a BigInteger
     * @throws SecurityException if decryption fails or the key is not found
     */
    override fun decryptPrivateKey(encryptedKey: ByteArray, keyAlias: String): BigInteger {
        try {
            // Get the encryption key from the KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey =
                keyStore.getKey(keyAlias, null) as? SecretKey
                    ?: throw SecurityException("No key found with alias: $keyAlias")

            // Extract IV from the beginning of the encrypted data
            val iv = encryptedKey.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = encryptedKey.copyOfRange(GCM_IV_LENGTH, encryptedKey.size)

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            // Decrypt the data
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            // Convert back to BigInteger
            return BigInteger(decryptedBytes)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt private key: ${e.message}", e)
        }
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
