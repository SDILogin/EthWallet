package me.sdi.ethwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.util.preferencesDataStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * [PinCodeStorage] backed by DataStore and AndroidKeyStore using an envelope scheme.
 *
 * A per-install [PBEKeySpec]-derived `pinKey` (PBKDF2WithHmacSHA256) wraps a random 256-bit master
 * KEK with software AES-256-GCM ([KekCipher]); that blob is wrapped again by an AndroidKeyStore GCM
 * key and persisted as `pin_wrapped_kek`, alongside the KDF salt (`pin_kdf_salt`). Verification is
 * decrypt-by-unwrap: a successful GCM unwrap both proves the PIN and yields the KEK, which is
 * published to the [PinSession] for the unlocked session. No PIN hash is stored.
 */
class PinCodeStorageDataStore(
    context: Context,
    private val userActivityManager: UserActivityManager,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val pinSession: PinSession,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinCodeStorage {
    companion object {
        private const val TAG = "DataStorePinCodeStorage"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PIN_KEY_ALIAS = "wallet_pin_code_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val PBKDF2_KEY_BITS = 256
        private const val SALT_LENGTH = 16
        private const val MASTER_KEK_LENGTH = 32

        private val PIN_KDF_SALT = stringPreferencesKey("pin_kdf_salt")
        private val PIN_WRAPPED_KEK = stringPreferencesKey("pin_wrapped_kek")
    }

    private val dataStore = context.preferencesDataStore

    override suspend fun storePinCode(pinCode: String) {
        pinAttemptsGuard.bootstrap()

        val salt = randomBytes(SALT_LENGTH)
        val masterKek = randomBytes(MASTER_KEK_LENGTH)
        val pinKey = deriveKey(pinCode, salt)
        val wrappedBase64 = try {
            encryptWithKeystore(
                secretKey = getOrCreateSecretKey(),
                payload = KekCipher.encrypt(pinKey, masterKek),
            )
        } finally {
            pinKey.fill(0)
        }

        dataStore.edit { preferences ->
            preferences[PIN_KDF_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            preferences[PIN_WRAPPED_KEK] = wrappedBase64
        }

        pinSession.set(masterKek)
        userActivityManager.onAppUnlocked()
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        if (!hasPinCode()) {
            Logger.debug(TAG) { "No PIN code stored for verification" }
            return false
        }

        val preferences = dataStore.data.first()
        val saltBase64 = preferences[PIN_KDF_SALT]
        val wrappedBase64 = preferences[PIN_WRAPPED_KEK]
        if (saltBase64 == null || wrappedBase64 == null) {
            return false
        }

        var pinKey: ByteArray? = null
        return try {
            pinKey = deriveKey(pinCode, Base64.decode(saltBase64, Base64.DEFAULT))
            val innerBlob = decryptWithKeystore(wrappedBase64)
            val masterKek = KekCipher.decrypt(pinKey, innerBlob)
            pinSession.set(masterKek)
            userActivityManager.onAppUnlocked()
            Logger.debug(TAG) { "PIN verification succeeded" }
            true
        } catch (e: GeneralSecurityException) {
            Logger.debug(TAG) { "PIN verification failed: ${e.message}" }
            false
        } catch (e: IllegalArgumentException) {
            Logger.debug(TAG) { "PIN envelope is malformed: ${e.message}" }
            false
        } finally {
            pinKey?.fill(0)
        }
    }

    override suspend fun hasPinCode(): Boolean {
        val preferences = dataStore.data.first()
        val hasSalt = preferences.contains(PIN_KDF_SALT)
        val hasWrapped = preferences.contains(PIN_WRAPPED_KEK)
        val hasKey = loadKeyStore().containsAlias(PIN_KEY_ALIAS)
        return hasSalt && hasWrapped && hasKey
    }

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_KDF_SALT)
            preferences.remove(PIN_WRAPPED_KEK)
        }

        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(PIN_KEY_ALIAS)) {
                keyStore.deleteEntry(PIN_KEY_ALIAS)
                Logger.debug(TAG) { "PIN code and key deleted successfully" }
            } else {
                Logger.debug(TAG) { "No key found to delete" }
            }
        }

        pinAttemptsGuard.clear()
        pinSession.clear()
    }

    private suspend fun randomBytes(size: Int): ByteArray = withContext(dispatcher) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun deriveKey(pinCode: String, salt: ByteArray): ByteArray =
        withContext(dispatcher) {
            val spec = PBEKeySpec(pinCode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
            try {
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).also { ensureActive() }
                factory.generateSecret(spec).encoded.also { ensureActive() }
            } finally {
                spec.clearPassword()
            }
        }

    private suspend fun getOrCreateSecretKey(): SecretKey =
        withContext(dispatcher) {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(PIN_KEY_ALIAS)) {
                return@withContext keyStore.getKey(PIN_KEY_ALIAS, null) as SecretKey
            }

            val keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .also { ensureActive() }

            val keyGenParameterSpec = KeyGenParameterSpec
                .Builder(PIN_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
            val secretKey = keyStore.getKey(PIN_KEY_ALIAS, null) as SecretKey
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
            require(encryptedData.size >= GCM_IV_LENGTH) { "Wrapped KEK blob is shorter than the IV" }

            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).also { ensureActive() }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(encrypted)
        }
}
