package me.sdi.ethwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
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
 * persisted as `keystore_wrapped_master_kek`. The PIN is an app-lock check only: it is stored as a salted
 * PBKDF2WithHmacSHA256 hash (`pin_hash`, `pin_hash_salt`) and does not take part in
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

        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_HASH_ITERATIONS = 200_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_HASH_SALT_BYTES = 16
        private const val MASTER_KEK_BYTES = 32

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEYSTORE_WRAPPED_MASTER_KEK = stringPreferencesKey("keystore_wrapped_master_kek")
    }

    private val dataStore = context.preferencesDataStore

    override suspend fun storePinCode(pinCode: String) {
        pinAttemptsGuard.bootstrap()

        val masterKek = randomBytes(MASTER_KEK_BYTES)
        val wrappedKekBase64 = encryptWithKeystore(secretKey = getOrCreateSecretKey(), payload = masterKek)
        writePinHash(pinCode) { preferences ->
            preferences[KEYSTORE_WRAPPED_MASTER_KEK] = wrappedKekBase64
        }

        pinSession.set(masterKek)
        userActivityManager.onAppUnlocked()
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = readArtifacts()

        val pinHash = hashPin(pinCode, Base64.decode(artifacts.pinHashSaltBase64, Base64.DEFAULT))
        val matches = try {
            MessageDigest.isEqual(pinHash, Base64.decode(artifacts.pinHashBase64, Base64.DEFAULT))
        } finally {
            pinHash.fill(0)
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
        writePinHash(newPin)
        Logger.debug(TAG) { "PIN code changed successfully" }
    }

    override suspend fun hasPinCode(): Boolean {
        val preferences = dataStore.data.first()
        return PIN_HASH_SALT in preferences &&
            PIN_HASH in preferences &&
            KEYSTORE_WRAPPED_MASTER_KEK in preferences &&
            loadKeyStore().containsAlias(PIN_KEY_ALIAS)
    }

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_HASH_SALT)
            preferences.remove(PIN_HASH)
            preferences.remove(KEYSTORE_WRAPPED_MASTER_KEK)
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

    private suspend fun readArtifacts(): PinArtifacts {
        val preferences = dataStore.data.first()
        return PinArtifacts(
            pinHashSaltBase64 = checkNotNull(preferences[PIN_HASH_SALT]) { "No PIN hash salt stored" },
            pinHashBase64 = checkNotNull(preferences[PIN_HASH]) { "No PIN hash stored" },
            wrappedKekBase64 = checkNotNull(preferences[KEYSTORE_WRAPPED_MASTER_KEK]) { "No wrapped KEK stored" },
        )
    }

    private class PinArtifacts(val pinHashSaltBase64: String, val pinHashBase64: String, val wrappedKekBase64: String)

    private suspend fun randomBytes(size: Int): ByteArray = withContext(dispatcher) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun writePinHash(pinCode: String, alsoWrite: (MutablePreferences) -> Unit = {}) {
        val salt = randomBytes(PIN_HASH_SALT_BYTES)
        val pinHash = hashPin(pinCode, salt)

        dataStore.edit { preferences ->
            preferences[PIN_HASH_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            preferences[PIN_HASH] = Base64.encodeToString(pinHash, Base64.DEFAULT)
            alsoWrite(preferences)
        }
    }

    private suspend fun hashPin(pinCode: String, salt: ByteArray): ByteArray =
        withContext(dispatcher) {
            val spec = PBEKeySpec(pinCode.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance(PIN_HASH_ALGORITHM).also { ensureActive() }
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
