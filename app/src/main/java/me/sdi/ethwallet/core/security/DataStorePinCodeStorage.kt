package me.sdi.ethwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.UserActivityManager
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Implementation of PinCodeStorage that uses DataStore for persistence and KeyStore for encryption.
 *
 * This class provides a secure way to store and verify PIN codes by:
 * 1. Hashing the PIN code using SHA-256
 * 2. Encrypting the hashed PIN using AES/GCM/NoPadding with a key from Android KeyStore
 * 3. Storing the encrypted PIN in Jetpack DataStore
 */
class DataStorePinCodeStorage(
    context: Context,
    private val userActivityManager: UserActivityManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinCodeStorage {
    companion object {
        private const val TAG = "DataStorePinCodeStorage"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PIN_KEY_ALIAS = "wallet_pin_code_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private val ENCRYPTED_PIN = stringPreferencesKey("encrypted_pin")
    }

    val dataStore = context.pinDataStore

    override suspend fun storePinCode(pinCode: String) {
        val hashedPin = hashPinCode(pinCode)
        val secretKey = getOrCreateSecretKey()
        val encryptedBase64 = encrypt(secretKey, hashedPin)
        dataStore.edit { preferences ->
            preferences[ENCRYPTED_PIN] = encryptedBase64
        }

        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        if (!hasPinCode()) {
            Logger.debug(TAG) { "No PIN code stored for verification" }
            return false
        }

        val hashedInputPin = hashPinCode(pinCode)
        val storedHashedPin = loadPin()
        val result = storedHashedPin.contentEquals(hashedInputPin)
        Logger.debug(TAG) { "PIN verification result: $result" }
        if (result) {
            userActivityManager.onAppUnlocked()
        }
        return result
    }

    private suspend fun loadPin(): ByteArray {
        val encryptedBase64: String? = dataStore.data.map { preferences ->
            preferences[ENCRYPTED_PIN]
        }.first()

        checkNotNull(encryptedBase64) {
            "Encrypted PIN code not found in DataStore"
        }

        val keyStore = loadKeyStore()
        val secretKey = keyStore.getKey(PIN_KEY_ALIAS, null) as SecretKey
        val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)

        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val storedHashedPin = cipher.doFinal(encrypted)
        return storedHashedPin
    }

    override suspend fun hasPinCode(): Boolean {
        return dataStore.data.map { preferences ->
            val hasPin = preferences.contains(ENCRYPTED_PIN)
            val keyStore = loadKeyStore()
            val hasKey = keyStore.containsAlias(PIN_KEY_ALIAS)
            hasPin && hasKey
        }.first()
    }

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(ENCRYPTED_PIN)
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
    }

    private suspend fun hashPinCode(pinCode: String): ByteArray =
        withContext(dispatcher) {
            val digest = MessageDigest.getInstance("SHA-256").also {
                ensureActive()
            }
            digest.digest(pinCode.toByteArray(StandardCharsets.UTF_8))
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

    private suspend fun encrypt(secretKey: SecretKey, payload: ByteArray): String =
        withContext(dispatcher) {
            val cipher = Cipher.getInstance(TRANSFORMATION).also { ensureActive() }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey).also { ensureActive() }

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(payload).also { ensureActive() }
            val combined = ByteArray(iv.size + encryptedBytes.size)

            System
                .arraycopy(iv, 0, combined, 0, iv.size)
                .also { ensureActive() }

            System
                .arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
                .also { ensureActive() }

            Base64.encodeToString(combined, Base64.DEFAULT)
        }
}

private const val DATASTORE_NAME = "secure_pin_preferences"
private val Context.pinDataStore by preferencesDataStore(name = DATASTORE_NAME)
