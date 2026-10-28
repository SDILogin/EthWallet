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
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * [PinCodeStorage] backed by DataStore and AndroidKeyStore.
 *
 * A random 256-bit master KEK is encrypted by the AndroidKeyStore GCM key `wallet_pin_code_key` and
 * persisted as `keystore_wrapped_kek`. The PIN is an app-lock check only: it is stored as a salted
 * PBKDF2WithHmacSHA256 verifier (`pin_verifier`, `pin_verifier_salt`) and does not take part in
 * encrypting the KEK. A matching PIN publishes the KEK to the [PinSession] for the unlocked session.
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

        private val PIN_VERIFIER_SALT = stringPreferencesKey("pin_verifier_salt")
        private val PIN_VERIFIER = stringPreferencesKey("pin_verifier")
        private val KEYSTORE_WRAPPED_KEK = stringPreferencesKey("keystore_wrapped_kek")
    }

    private val dataStore = context.preferencesDataStore

    override suspend fun storePinCode(pinCode: String) {
        pinAttemptsGuard.bootstrap()

        val salt = randomBytes(SALT_LENGTH)
        val verifier = deriveVerifier(pinCode, salt)
        val masterKek = randomBytes(MASTER_KEK_LENGTH)
        val wrappedKekBase64 = encryptWithKeystore(secretKey = getOrCreateSecretKey(), payload = masterKek)

        dataStore.edit { preferences ->
            preferences[PIN_VERIFIER_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            preferences[PIN_VERIFIER] = Base64.encodeToString(verifier, Base64.DEFAULT)
            preferences[KEYSTORE_WRAPPED_KEK] = wrappedKekBase64
        }

        pinSession.set(masterKek)
        userActivityManager.onAppUnlocked()
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = checkNotNull(readArtifacts()) { "No PIN code stored for verification" }

        val verifier = deriveVerifier(pinCode, Base64.decode(artifacts.saltBase64, Base64.DEFAULT))
        val matches = try {
            MessageDigest.isEqual(verifier, Base64.decode(artifacts.verifierBase64, Base64.DEFAULT))
        } finally {
            verifier.fill(0)
        }
        if (!matches) {
            Logger.debug(TAG) { "PIN verification failed" }
            return false
        }

        pinSession.set(decryptWithKeystore(artifacts.wrappedKekBase64))
        userActivityManager.onAppUnlocked()
        Logger.debug(TAG) { "PIN verification succeeded" }
        return true
    }

    override suspend fun changePinCode(newPin: String) {
        val salt = randomBytes(SALT_LENGTH)
        val verifier = deriveVerifier(newPin, salt)

        dataStore.edit { preferences ->
            checkNotNull(preferences[PIN_VERIFIER]) { "No PIN code stored to change" }
            preferences[PIN_VERIFIER_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            preferences[PIN_VERIFIER] = Base64.encodeToString(verifier, Base64.DEFAULT)
        }

        Logger.debug(TAG) { "PIN code changed successfully" }
    }

    override suspend fun hasPinCode(): Boolean = readArtifacts() != null

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_VERIFIER_SALT)
            preferences.remove(PIN_VERIFIER)
            preferences.remove(KEYSTORE_WRAPPED_KEK)
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

    private suspend fun readArtifacts(): PinArtifacts? {
        val preferences = dataStore.data.first()
        val saltBase64 = preferences[PIN_VERIFIER_SALT] ?: return null
        val verifierBase64 = preferences[PIN_VERIFIER] ?: return null
        val wrappedKekBase64 = preferences[KEYSTORE_WRAPPED_KEK] ?: return null
        if (!loadKeyStore().containsAlias(PIN_KEY_ALIAS)) {
            return null
        }

        return PinArtifacts(saltBase64, verifierBase64, wrappedKekBase64)
    }

    private class PinArtifacts(val saltBase64: String, val verifierBase64: String, val wrappedKekBase64: String)

    private suspend fun randomBytes(size: Int): ByteArray = withContext(dispatcher) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun deriveVerifier(pinCode: String, salt: ByteArray): ByteArray =
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
