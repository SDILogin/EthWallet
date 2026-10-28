package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

/**
 * Wipes the stored PIN verifier and master KEK on logout.
 *
 * The wipe goes through [dataStore] rather than deleting its file: the store keeps the artifacts
 * cached in memory, so a file deleted behind its back would leave the old PIN still verifying until
 * the app restarts.
 */
class DataCleanerPinArtifacts(private val dataStore: DataStore<Preferences>) : DataCleaner {
    override suspend fun clean() {
        dataStore.edit { preferences -> preferences.clear() }
    }
}
