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
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64

/**
 * [PinCodeStorage] for targets without a hardware-backed key store, persisted with [DataStore].
 *
 * A `pinKey` derived from the PIN (PBKDF2WithHmacSHA256) wraps a random 256-bit master KEK with
 * software AES-256-GCM ([KekCipher]). The KDF salt and that wrapped blob are the only artifacts
 * persisted; no PIN hash is ever stored. Verification is decrypt-by-unwrap: a successful GCM unwrap
 * both proves the PIN and yields the KEK, which is published to the [PinSession] for the session.
 *
 * With no hardware layer to bind the envelope to the machine, an attacker who copies the artifacts
 * can brute force the PIN offline at the cost of the KDF alone. That is weaker than the Android
 * storage, which wraps the same blob again with an AndroidKeyStore key. Open the store through
 * [pinArtifactsDataStore], which keeps the artifacts where only their owner can read them.
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
        val masterKek = randomBytes(MASTER_KEK_LENGTH)
        val pinKey = deriveKey(pinCode, salt)
        val wrappedKek = try {
            KekCipher.encrypt(pinKey, masterKek)
        } finally {
            pinKey.fill(0)
        }

        dataStore.edit { preferences ->
            preferences[PIN_KDF_SALT] = Base64.encode(salt)
            preferences[PIN_WRAPPED_KEK] = Base64.encode(wrappedKek)
        }

        pinSession.set(masterKek)
        Logger.debug(TAG) { "PIN code stored successfully" }
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        val artifacts = readArtifacts()
        if (artifacts == null) {
            Logger.debug(TAG) { "No PIN code stored for verification" }
            return false
        }

        var pinKey: ByteArray? = null
        return try {
            pinKey = deriveKey(pinCode, Base64.decode(artifacts.salt))
            val masterKek = KekCipher.decrypt(pinKey, Base64.decode(artifacts.wrappedKek))
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

    override suspend fun hasPinCode(): Boolean = readArtifacts() != null

    override suspend fun clearPinCode() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_KDF_SALT)
            preferences.remove(PIN_WRAPPED_KEK)
        }

        pinAttemptsGuard.clear()
        pinSession.clear()
    }

    private suspend fun randomBytes(size: Int): ByteArray = withContext(Dispatchers.Default) {
        ByteArray(size).also(SecureRandom()::nextBytes)
    }

    private suspend fun deriveKey(pinCode: String, salt: ByteArray): ByteArray =
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

        val salt = preferences[PIN_KDF_SALT]
        val wrappedKek = preferences[PIN_WRAPPED_KEK]
        return if (salt == null || wrappedKek == null) null else PinArtifacts(salt, wrappedKek)
    }

    private class PinArtifacts(val salt: String, val wrappedKek: String)

    private companion object {
        private const val TAG = "PinCodeStorageSoftware"

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val PBKDF2_KEY_BITS = 256
        private const val SALT_LENGTH = 16
        private const val MASTER_KEK_LENGTH = 32

        private val PIN_KDF_SALT = stringPreferencesKey("pin_kdf_salt")
        private val PIN_WRAPPED_KEK = stringPreferencesKey("pin_wrapped_kek")
    }
}
