package me.sdi.ethwallet.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.kotlin.PrivateKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts wallet private keys with two GCM layers: an inner software AES-256-GCM layer keyed by
 * the session master KEK ([KekCipher]) and an outer per-address AndroidKeyStore layer. Signing is
 * therefore impossible while the [PinSession] is locked.
 *
 * ```
 * pk -- private key.
 * epk -- encrypted private key.
 * h -- hardware encryption. Used to prevent decryption by pin code brutforce attack from outside of a device.
 * s -- software encryption. Used to prevent accessing the private key from the application process when
 *      attacker does not know the pin. PBKDF2(pin)
 *
 * Encryption: epk = h(s(pk))
 * Decryption: pk = s(h(epk))
 * ```
 */
class KeyStoreEncryptor(
    private val pinSession: PinSession,
) : PrivateKeyEncryptor {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val ETHEREUM_KEY_ALIAS_PREFIX = "eth_wallet_key_"
    }

    override suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String {
        val keyAlias = generateKeyAlias(address).also {
            Logger.debug("KeyStoreEncryptor") {
                "Encrypting private key for address $address with alias $it"
            }
        }
        val androidKeyStoreBackedSecretKey = getOrCreateAndroidKeyStoreBackedSecretKey(keyAlias)

        return withContext(Dispatchers.Default) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, androidKeyStoreBackedSecretKey)

            val iv = cipher.iv
            val kek = pinSession.getOrThrow()
            val plaintext = privateKey.copyBytes()
            try {
                val privateKeyEncryptedWithoutHardwareSecureModule = KekCipher.encrypt(
                    key = kek,
                    plaintext = plaintext,
                )
                try {
                    val privateKeyEncryptedWithHardwareSecureModule = cipher
                        .doFinal(privateKeyEncryptedWithoutHardwareSecureModule)
                    try {
                        val combined = ByteArray(iv.size + privateKeyEncryptedWithHardwareSecureModule.size)
                        System.arraycopy(iv, 0, combined, 0, iv.size)
                        System.arraycopy(
                            privateKeyEncryptedWithHardwareSecureModule,
                            0,
                            combined,
                            iv.size,
                            privateKeyEncryptedWithHardwareSecureModule.size,
                        )
                        return@withContext Base64.encodeToString(combined, Base64.DEFAULT)
                    } finally {
                        privateKeyEncryptedWithHardwareSecureModule.fill(0)
                    }
                } finally {
                    privateKeyEncryptedWithoutHardwareSecureModule.fill(0)
                }
            } finally {
                plaintext.fill(0)
            }
        }
    }

    override suspend fun decryptPrivateKey(
        encryptedBase64: String,
        address: String,
    ): PrivateKey = withContext(Dispatchers.Default) {
        val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val keyAlias = generateKeyAlias(address).also {
            Logger.debug("KeyStoreEncryptor") {
                "Decrypting private key for address $address with alias $it"
            }
        }

        val androidKeyStoreBackedSecretKey = keyStore.getKey(keyAlias, null) as SecretKey
        cipher.init(Cipher.DECRYPT_MODE, androidKeyStoreBackedSecretKey, spec)

        val privateKeyEncryptedWithHardwareSecureModule = cipher.doFinal(encrypted)
        try {
            val kek = pinSession.getOrThrow()
            val decryptedBytes = KekCipher.decrypt(kek, privateKeyEncryptedWithHardwareSecureModule)
            try {
                PrivateKey.fromBytes(decryptedBytes)
            } finally {
                decryptedBytes.fill(0)
            }
        } finally {
            privateKeyEncryptedWithHardwareSecureModule.fill(0)
        }
    }

    private fun generateKeyAlias(address: String) = "$ETHEREUM_KEY_ALIAS_PREFIX${address.lowercase()}"

    private suspend fun getOrCreateAndroidKeyStoreBackedSecretKey(
        keyAlias: String,
    ): SecretKey = withContext(Dispatchers.Default) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }

        if (keyStore.containsAlias(keyAlias)) {
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
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
            keyGenerator.generateKey()
        }
    }
}
