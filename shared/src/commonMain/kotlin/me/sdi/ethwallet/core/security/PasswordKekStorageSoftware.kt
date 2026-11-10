package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import kotlin.io.encoding.Base64

private const val MASTER_KEK_BYTES = 32
private const val SALT_BYTES = 16

/**
 * [PasswordKekStorage] persisted with [DataStore] opened through [pinArtifactsDataStore].
 *
 * The master KEK is encrypted with AES-256-GCM under a key that [keyDerivation] derives from the NFKC-normalized
 * password and a random salt. [protection] then wraps that blob, so on Desktop the stored data is bound to this Mac's
 * Secure Enclave and a copy taken off this Mac cannot even be tested against a password guess. On this Mac, a process
 * of the same user can remove the Secure Enclave layer, so there the password strength and the key derivation cost
 * are the protection. A wrong password fails the GCM tag of the inner layer.
 */
class PasswordKekStorageSoftware(
    private val dataStore: DataStore<Preferences>,
    private val protection: MasterKekProtection,
    private val keyDerivation: PasswordKeyDerivation,
) : PasswordKekStorage {
    override suspend fun create(password: String): ByteArray {
        val (masterKek, salt) = withContext(Dispatchers.Default) {
            val random = SecureRandom()
            ByteArray(MASTER_KEK_BYTES).also(random::nextBytes) to ByteArray(SALT_BYTES).also(random::nextBytes)
        }
        val passwordKey = derivePasswordKey(password = password, salt = salt)
        val passwordWrapped = try {
            KekCipher.encrypt(passwordKey, masterKek)
        } finally {
            passwordKey.fill(0)
        }
        val wrappingKey = protection.createWrappingKey()
        val wrapped = protection.wrap(wrappingKey = wrappingKey, payload = passwordWrapped)
        dataStore.edit { preferences ->
            preferences[KEK_WRAPPING_KEY] = Base64.encode(wrappingKey)
            preferences[KEK_PASSWORD_SALT] = Base64.encode(salt)
            preferences[PASSWORD_WRAPPED_MASTER_KEK] = Base64.encode(wrapped)
        }
        return masterKek
    }

    override suspend fun read(password: String): ByteArray? {
        val preferences = dataStore.data.first()
        val wrappingKey = checkNotNull(preferences[KEK_WRAPPING_KEY]) { "No KEK wrapping key stored" }
        val salt = checkNotNull(preferences[KEK_PASSWORD_SALT]) { "No KEK password salt stored" }
        val wrapped = checkNotNull(preferences[PASSWORD_WRAPPED_MASTER_KEK]) { "No master KEK stored" }

        val passwordWrapped = protection.unwrap(wrappingKey = Base64.decode(wrappingKey), wrapped = Base64.decode(wrapped))
        val passwordKey = derivePasswordKey(password = password, salt = Base64.decode(salt))
        return try {
            KekCipher.decrypt(passwordKey, passwordWrapped)
        } catch (e: AEADBadTagException) {
            null
        } finally {
            passwordKey.fill(0)
        }
    }

    override suspend fun exists(): Boolean = PASSWORD_WRAPPED_MASTER_KEK in dataStore.data.first()

    private suspend fun derivePasswordKey(password: String, salt: ByteArray): ByteArray {
        val passwordBytes = Normalizer.normalize(password, Normalizer.Form.NFKC).encodeToByteArray()
        return try {
            keyDerivation.deriveKey(password = passwordBytes, salt = salt)
        } finally {
            passwordBytes.fill(0)
        }
    }

    private companion object {
        private val KEK_WRAPPING_KEY = stringPreferencesKey("kek_wrapping_key")
        private val KEK_PASSWORD_SALT = stringPreferencesKey("kek_password_salt")
        private val PASSWORD_WRAPPED_MASTER_KEK = stringPreferencesKey("password_wrapped_master_kek")
    }
}
