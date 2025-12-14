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
class KeyStoreEncryptor : PrivateKeyEncryptor {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val ETHEREUM_KEY_ALIAS_PREFIX = "eth_wallet_key_"
    }

    override fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
        try {
            val keyAlias = generateKeyAlias(address)
            Logger.debug("KeyStoreEncryptor") {
                "Encrypting private key for address $address with alias $keyAlias"
            }

            val secretKey = getOrCreateSecretKey(keyAlias)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv

            val privateKeyBytes = privateKey.toByteArray()
            val encryptedBytes = cipher.doFinal(privateKeyBytes)

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.debug("KeyStoreEncryptor") {
                "Error encrypting private key: ${e.message}"
            }
            throw SecurityException("Failed to encrypt private key: ${e.message}", e)
        }
    }

    override fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
        try {
            val keyAlias = generateKeyAlias(address)
            Logger.debug("KeyStoreEncryptor") {
                "Decrypting private key for address $address with alias $keyAlias"
            }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey =
                keyStore.getKey(keyAlias, null) as? SecretKey
                    ?: throw SecurityException("No key found with alias: $keyAlias")

            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)

            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encrypted)

            return BigInteger(decryptedBytes)
        } catch (e: Exception) {
            Logger.debug("KeyStoreEncryptor") {
                "Error decrypting private key: ${e.message}"
            }
            throw SecurityException("Failed to decrypt private key: ${e.message}", e)
        }
    }

    private fun generateKeyAlias(address: String): String {
        return "$ETHEREUM_KEY_ALIAS_PREFIX${address.lowercase()}"
    }

    private fun getOrCreateSecretKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

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
                .setUserAuthenticationRequired(false)
                .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
