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
 * [MasterKekStorage] for targets without a hardware-backed key store, persisted with [DataStore].
 *
 * The random 256-bit master KEK is stored as plain Base64. Its only protection is the file system:
 * open the store through [pinArtifactsDataStore], which keeps it where only its owner can read it.
 * Anyone who can read the store, or the memory of the app process, has the KEK without the PIN.
 */
class MasterKekStorageSoftware(
    private val dataStore: DataStore<Preferences>,
) : MasterKekStorage {
    override suspend fun create(): ByteArray {
        val masterKek = withContext(Dispatchers.Default) {
            ByteArray(MASTER_KEK_BYTES).also(SecureRandom()::nextBytes)
        }
        dataStore.edit { preferences -> preferences[MASTER_KEK] = Base64.encode(masterKek) }
        return masterKek
    }

    override suspend fun read(): ByteArray {
        val masterKek = checkNotNull(dataStore.data.first()[MASTER_KEK]) { "No master KEK stored" }
        return Base64.decode(masterKek)
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(MASTER_KEK) }
    }

    private companion object {
        private const val MASTER_KEK_BYTES = 32

        private val MASTER_KEK = stringPreferencesKey("master_kek")
    }
}
