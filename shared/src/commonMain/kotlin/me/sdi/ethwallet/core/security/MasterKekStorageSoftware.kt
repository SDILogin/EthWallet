package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/**
 * [MasterKekStorage] for targets without AndroidKeyStore, persisted with [DataStore] opened through
 * [pinArtifactsDataStore].
 *
 * The random 256-bit master KEK is never stored in plain form. [protection] wraps it, and the store keeps only the
 * wrapped KEK and the opaque handle of the wrapping key, so the store alone is as strong as [protection]. The handle
 * is the only stored form of the wrapping key, so [clear] deletes the key by removing the handle.
 */
class MasterKekStorageSoftware(
    private val dataStore: DataStore<Preferences>,
    private val protection: MasterKekProtection,
) : MasterKekStorage {
    override suspend fun create(): ByteArray {
        val masterKek = withContext(Dispatchers.Default) {
            ByteArray(MASTER_KEK_BYTES).also(SecureRandom()::nextBytes)
        }
        val wrappingKey = protection.createWrappingKey()
        val wrappedMasterKek = protection.wrap(wrappingKey = wrappingKey, masterKek = masterKek)
        dataStore.edit { preferences ->
            preferences[KEK_WRAPPING_KEY] = Base64.encode(wrappingKey)
            preferences[WRAPPED_MASTER_KEK] = Base64.encode(wrappedMasterKek)
        }
        return masterKek
    }

    override suspend fun read(): ByteArray {
        val preferences = dataStore.data.first()
        val wrappingKey = checkNotNull(preferences[KEK_WRAPPING_KEY]) { "No KEK wrapping key stored" }
        val wrappedMasterKek = checkNotNull(preferences[WRAPPED_MASTER_KEK]) { "No master KEK stored" }
        return protection.unwrap(
            wrappingKey = Base64.decode(wrappingKey),
            wrappedMasterKek = Base64.decode(wrappedMasterKek),
        )
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEK_WRAPPING_KEY)
            preferences.remove(WRAPPED_MASTER_KEK)
        }
    }

    private companion object {
        private const val MASTER_KEK_BYTES = 32

        private val KEK_WRAPPING_KEY = stringPreferencesKey("kek_wrapping_key")
        private val WRAPPED_MASTER_KEK = stringPreferencesKey("wrapped_master_kek")
    }
}
