package me.sdi.ethwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.util.preferencesDataStore
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [MasterKekStorage] backed by DataStore and AndroidKeyStore.
 *
 * The random 256-bit master KEK is encrypted by the AndroidKeyStore GCM key `wallet_pin_code_key` and
 * persisted as `keystore_wrapped_master_kek`.
 */
class MasterKekStorageAndroidKeyStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher,
) : MasterKekStorage {
    private val dataStore = context.preferencesDataStore

    override suspend fun create(): ByteArray {
        val masterKek = withContext(dispatcher) {
            ByteArray(MASTER_KEK_BYTES).also(SecureRandom()::nextBytes)
        }
        val wrappedKekBase64 = encryptWithKeystore(secretKey = getOrCreateSecretKey(), payload = masterKek)
        dataStore.edit { preferences -> preferences[KEYSTORE_WRAPPED_MASTER_KEK] = wrappedKekBase64 }
        return masterKek
    }

    override suspend fun read(): ByteArray {
        val wrappedKekBase64 =
            checkNotNull(dataStore.data.first()[KEYSTORE_WRAPPED_MASTER_KEK]) { "No wrapped KEK stored" }
        return decryptWithKeystore(wrappedKekBase64)
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEYSTORE_WRAPPED_MASTER_KEK) }

        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(KEK_WRAPPING_KEY_ALIAS)) {
                keyStore.deleteEntry(KEK_WRAPPING_KEY_ALIAS)
                Logger.debug(TAG) { "Master KEK wrapping key deleted" }
            }
        }
    }

    private suspend fun getOrCreateSecretKey(): SecretKey =
        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(KEK_WRAPPING_KEY_ALIAS)) {
                return@withContext keyStore.getKey(KEK_WRAPPING_KEY_ALIAS, null) as SecretKey
            }

            val keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .also { ensureActive() }

            val keyGenParameterSpec = KeyGenParameterSpec
                .Builder(KEK_WRAPPING_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec).also {
                ensureActive()
            }
            return@withContext keyGenerator.generateKey()
        }

    private suspend fun loadKeyStore(): KeyStore = withContext(dispatcher) {
        val keyStore = KeyStore
            .getInstance(ANDROID_KEYSTORE)
            .also { ensureActive() }

        keyStore
            .load(null)
            .also { ensureActive() }

        return@withContext keyStore
    }

    private suspend fun encryptWithKeystore(secretKey: SecretKey, payload: ByteArray): String =
        withContext(dispatcher) {
            val cipher = Cipher.getInstance(TRANSFORMATION).also { ensureActive() }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey).also { ensureActive() }

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(payload).also { ensureActive() }
            Base64.encodeToString(iv + encryptedBytes, Base64.DEFAULT)
        }

    private suspend fun decryptWithKeystore(encryptedBase64: String): ByteArray =
        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            val secretKey = keyStore.getKey(KEK_WRAPPING_KEY_ALIAS, null) as SecretKey
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
            require(encryptedData.size >= GCM_IV_LENGTH) { "Wrapped KEK blob is shorter than the IV" }

            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).also { ensureActive() }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(encrypted)
        }

    private companion object {
        private const val TAG = "MasterKekStorage"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEK_WRAPPING_KEY_ALIAS = "wallet_pin_code_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val MASTER_KEK_BYTES = 32

        private val KEYSTORE_WRAPPED_MASTER_KEK = stringPreferencesKey("keystore_wrapped_master_kek")
    }
}
