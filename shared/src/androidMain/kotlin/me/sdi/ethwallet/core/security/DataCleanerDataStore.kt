package me.sdi.ethwallet.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.util.preferencesDataStore

class DataCleanerDataStore(context: Context) : DataCleaner {
    private val appContext = context.applicationContext

    override suspend fun clean() {
        val preferencesDataStore = withContext(Dispatchers.IO) {
            appContext.preferencesDataStore
        }
        preferencesDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
