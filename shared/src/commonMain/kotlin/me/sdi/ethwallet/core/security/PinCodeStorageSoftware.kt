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
import me.sdi.ethwallet.data.UserActivityManager
import java.io.IOException
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
 * The PIN is an app-lock check only. It is stored as a salted PBKDF2WithHmacSHA256 verifier, which
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

        val salt = randomBytes(SALT_LENGTH)
        val verifier = deriveVerifier(pinCode, salt)
        val masterKek = randomBytes(MASTER_KEK_LENGTH)

        dataStore.edit { preferences ->
            preferences[PIN_VERIFIER_SALT] = Base64.encode(salt)
            preferences[PIN_VERIFIER] = Base64.encode(verifier)
            preferences[MASTER_KEK] = Base64.encode(masterKek)
        }

        pinSession.set(masterKek)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = checkNotNull(readArtifacts()) { "No PIN code stored for verification" }

        val verifier = deriveVerifier(pinCode, Base64.decode(artifacts.salt))
        val matches = try {
            MessageDigest.isEqual(verifier, Base64.decode(artifacts.verifier))
        } finally {
            verifier.fill(0)
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
        val salt = randomBytes(SALT_LENGTH)
        val verifier = deriveVerifier(newPin, salt)

        dataStore.edit { preferences ->
            checkNotNull(preferences[PIN_VERIFIER]) { "No PIN code stored to change" }
            preferences[PIN_VERIFIER_SALT] = Base64.encode(salt)
            preferences[PIN_VERIFIER] = Base64.encode(verifier)
        }

        Logger.debug(TAG) { "PIN code changed successfully" }
    }

    override suspend fun hasPinCode(): Boolean = readArtifacts() != null

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_VERIFIER_SALT)
            preferences.remove(PIN_VERIFIER)
            preferences.remove(MASTER_KEK)
        }

        pinAttemptsGuard.clear()
        pinSession.clear()
    }

    private suspend fun randomBytes(size: Int): ByteArray = withContext(Dispatchers.Default) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun deriveVerifier(pinCode: String, salt: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val spec = PBEKeySpec(pinCode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
            try {
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).also { ensureActive() }
                factory.generateSecret(spec).encoded.also { ensureActive() }
            } finally {
                spec.clearPassword()
            }
        }

    private suspend fun readArtifacts(): PinArtifacts? {
        val preferences = try {
            dataStore.data.first()
        } catch (e: IOException) {
            Logger.warn(TAG, e) { "Failed to read the stored PIN artifacts" }
            return null
        }

        val salt = preferences[PIN_VERIFIER_SALT] ?: return null
        val verifier = preferences[PIN_VERIFIER] ?: return null
        val masterKek = preferences[MASTER_KEK] ?: return null
        return PinArtifacts(salt, verifier, masterKek)
    }

    private class PinArtifacts(val salt: String, val verifier: String, val masterKek: String)

    private companion object {
        private const val TAG = "PinCodeStorageSoftware"

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val PBKDF2_KEY_BITS = 256
        private const val SALT_LENGTH = 16
        private const val MASTER_KEK_LENGTH = 32

        private val PIN_VERIFIER_SALT = stringPreferencesKey("pin_verifier_salt")
        private val PIN_VERIFIER = stringPreferencesKey("pin_verifier")
        private val MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
