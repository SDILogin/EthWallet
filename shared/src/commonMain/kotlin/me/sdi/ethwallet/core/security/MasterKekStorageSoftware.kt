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
 *
 * [read] also migrates the plain `master_kek` written by earlier versions: it wraps that KEK and removes the plain
 * value. The KEK keeps its value, so a copy of the store taken before the migration still decrypts the private keys.
 */
class MasterKekStorageSoftware(
    private val dataStore: DataStore<Preferences>,
    private val protection: MasterKekProtection,
) : MasterKekStorage {
    override suspend fun create(): ByteArray {
        val masterKek = withContext(Dispatchers.Default) {
            ByteArray(MASTER_KEK_BYTES).also(SecureRandom()::nextBytes)
        }
        writeWrapped(masterKek)
        return masterKek
    }

    override suspend fun read(): ByteArray {
        val preferences = dataStore.data.first()
        val wrappedMasterKek = preferences[WRAPPED_MASTER_KEK]
        return if (wrappedMasterKek != null) {
            val wrappingKey = checkNotNull(preferences[KEK_WRAPPING_KEY]) { "No KEK wrapping key stored" }
            protection.unwrap(
                wrappingKey = Base64.decode(wrappingKey),
                wrappedMasterKek = Base64.decode(wrappedMasterKek),
            )
        } else {
            val legacyMasterKek = checkNotNull(preferences[LEGACY_MASTER_KEK]) { "No master KEK stored" }
            Base64.decode(legacyMasterKek).also { masterKek -> writeWrapped(masterKek) }
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEK_WRAPPING_KEY)
            preferences.remove(WRAPPED_MASTER_KEK)
            preferences.remove(LEGACY_MASTER_KEK)
        }
    }

    private suspend fun writeWrapped(masterKek: ByteArray) {
        val wrappingKey = protection.createWrappingKey()
        val wrappedMasterKek = protection.wrap(wrappingKey = wrappingKey, masterKek = masterKek)
        dataStore.edit { preferences ->
            preferences[KEK_WRAPPING_KEY] = Base64.encode(wrappingKey)
            preferences[WRAPPED_MASTER_KEK] = Base64.encode(wrappedMasterKek)
            preferences.remove(LEGACY_MASTER_KEK)
        }
    }

    private companion object {
        private const val MASTER_KEK_BYTES = 32

        private val KEK_WRAPPING_KEY = stringPreferencesKey("kek_wrapping_key")
        private val WRAPPED_MASTER_KEK = stringPreferencesKey("wrapped_master_kek")
        private val LEGACY_MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
