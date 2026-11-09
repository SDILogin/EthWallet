package me.sdi.ethwallet.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.util.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * [PinCodeStorage] backed by DataStore.
 *
 * The PIN is an app-lock check only: it is stored as a salted PBKDF2WithHmacSHA256 hash (`pin_hash`,
 * `pin_hash_salt`) and does not take part in encrypting any key.
 */
class PinCodeStorageDataStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher,
) : PinCodeStorage {
    private val dataStore = context.preferencesDataStore

    override suspend fun storePinCode(pinCode: String) {
        writePinHash(pinCode)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val preferences = dataStore.data.first()
        val pinHashSaltBase64 = checkNotNull(preferences[PIN_HASH_SALT]) { "No PIN hash salt stored" }
        val pinHashBase64 = checkNotNull(preferences[PIN_HASH]) { "No PIN hash stored" }

        val pinHash = hashPin(pinCode, Base64.decode(pinHashSaltBase64, Base64.DEFAULT))
        val matches = try {
            MessageDigest.isEqual(pinHash, Base64.decode(pinHashBase64, Base64.DEFAULT))
        } finally {
            pinHash.fill(0)
        }
        Logger.debug(TAG) { if (matches) "PIN verification succeeded" else "PIN verification failed" }
        return matches
    }

    override suspend fun changePinCode(newPin: String) {
        writePinHash(newPin)
        Logger.debug(TAG) { "PIN code changed successfully" }
    }

    override suspend fun hasPinCode(): Boolean = PIN_HASH in dataStore.data.first()

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_HASH_SALT)
            preferences.remove(PIN_HASH)
        }
    }

    private suspend fun writePinHash(pinCode: String) {
        val salt = withContext(dispatcher) {
            ByteArray(PIN_HASH_SALT_BYTES).also(SecureRandom()::nextBytes)
        }
        val pinHash = hashPin(pinCode, salt)

        dataStore.edit { preferences ->
            preferences[PIN_HASH_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            preferences[PIN_HASH] = Base64.encodeToString(pinHash, Base64.DEFAULT)
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

    private companion object {
        private const val TAG = "DataStorePinCodeStorage"

        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_HASH_ITERATIONS = 200_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_HASH_SALT_BYTES = 16

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
    }
}
