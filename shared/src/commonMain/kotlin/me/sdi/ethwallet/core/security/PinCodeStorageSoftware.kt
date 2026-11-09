package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64

/**
 * [PinCodeStorage] for targets without a hardware-backed key store, persisted with [DataStore].
 *
 * The PIN is an app-lock check only. It is stored as a salted PBKDF2WithHmacSHA256 PIN hash, which
 * can be brute forced offline by anyone who can read the store.
 */
class PinCodeStorageSoftware(
    private val dataStore: DataStore<Preferences>,
) : PinCodeStorage {
    override suspend fun storePinCode(pinCode: String) {
        writePinHash(pinCode)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val preferences = dataStore.data.first()
        val pinHashSalt = checkNotNull(preferences[PIN_HASH_SALT]) { "No PIN hash salt stored" }
        val storedPinHash = checkNotNull(preferences[PIN_HASH]) { "No PIN hash stored" }

        val pinHash = hashPin(pinCode, Base64.decode(pinHashSalt))
        val matches = try {
            MessageDigest.isEqual(pinHash, Base64.decode(storedPinHash))
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
        val salt = withContext(Dispatchers.Default) {
            ByteArray(PIN_HASH_SALT_BYTES).also(SecureRandom()::nextBytes)
        }
        val pinHash = hashPin(pinCode, salt)

        dataStore.edit { preferences ->
            preferences[PIN_HASH_SALT] = Base64.encode(salt)
            preferences[PIN_HASH] = Base64.encode(pinHash)
        }
    }

    private suspend fun hashPin(pinCode: String, salt: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val spec = PBEKeySpec(pinCode.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance(PIN_HASH_ALGORITHM).also { ensureActive() }
                factory.generateSecret(spec).encoded.also { ensureActive() }
            } finally {
                spec.clearPassword()
            }
        }

    private companion object {
        private const val TAG = "PinCodeStorageSoftware"

        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_HASH_ITERATIONS = 200_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_HASH_SALT_BYTES = 16

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
    }
}
