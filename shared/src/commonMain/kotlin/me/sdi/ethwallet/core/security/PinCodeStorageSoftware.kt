package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.UserActivityManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64

/**
 * [PinCodeStorage] for targets without a hardware-backed key store, persisted with [DataStore].
 *
 * The random 256-bit master KEK is stored as plain Base64. Its only protection is the file system:
 * open the store through [pinArtifactsDataStore], which keeps it where only its owner can read it.
 * Anyone who can read the store, or the memory of the app process, has the KEK without the PIN.
 *
 * The PIN is an app-lock check only. It is stored as a salted PBKDF2WithHmacSHA256 PIN hash, which
 * can be brute forced offline by anyone who can read the store.
 */
class PinCodeStorageSoftware(
    private val dataStore: DataStore<Preferences>,
    private val userActivityManager: UserActivityManager,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val pinSession: PinSession,
) : PinCodeStorage {
    override suspend fun storePinCode(pinCode: String) {
        pinAttemptsGuard.bootstrap()

        val masterKek = randomBytes(MASTER_KEK_BYTES)
        writePinHash(pinCode) { preferences ->
            preferences[MASTER_KEK] = Base64.encode(masterKek)
        }

        pinSession.set(masterKek)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = readArtifacts()

        val pinHash = hashPin(pinCode, Base64.decode(artifacts.pinHashSalt))
        val matches = try {
            MessageDigest.isEqual(pinHash, Base64.decode(artifacts.pinHash))
        } finally {
            pinHash.fill(0)
        }
        if (!matches) {
            Logger.debug(TAG) { "PIN verification failed" }
            return false
        }

        pinSession.set(Base64.decode(artifacts.masterKek))
        userActivityManager.onAppUnlocked()
        Logger.debug(TAG) { "PIN verification succeeded" }
        return true
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
            preferences.remove(MASTER_KEK)
        }

        pinAttemptsGuard.clear()
        pinSession.clear()
    }

    private suspend fun randomBytes(size: Int): ByteArray = withContext(Dispatchers.Default) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun writePinHash(pinCode: String, alsoWrite: (MutablePreferences) -> Unit = {}) {
        val salt = randomBytes(PIN_HASH_SALT_BYTES)
        val pinHash = hashPin(pinCode, salt)

        dataStore.edit { preferences ->
            preferences[PIN_HASH_SALT] = Base64.encode(salt)
            preferences[PIN_HASH] = Base64.encode(pinHash)
            alsoWrite(preferences)
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

    private suspend fun readArtifacts(): PinArtifacts {
        val preferences = dataStore.data.first()
        return PinArtifacts(
            pinHashSalt = checkNotNull(preferences[PIN_HASH_SALT]) { "No PIN hash salt stored" },
            pinHash = checkNotNull(preferences[PIN_HASH]) { "No PIN hash stored" },
            masterKek = checkNotNull(preferences[MASTER_KEK]) { "No master KEK stored" },
        )
    }

    private class PinArtifacts(val pinHashSalt: String, val pinHash: String, val masterKek: String)

    private companion object {
        private const val TAG = "PinCodeStorageSoftware"

        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_HASH_ITERATIONS = 200_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_HASH_SALT_BYTES = 16
        private const val MASTER_KEK_BYTES = 32

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
