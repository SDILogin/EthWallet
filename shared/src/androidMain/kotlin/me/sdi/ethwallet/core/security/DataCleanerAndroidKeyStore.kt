package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore

class DataCleanerAndroidKeyStore : DataCleaner {
    override suspend fun clean() = withContext(Dispatchers.IO) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.aliases().toList().forEach { alias ->
            keyStore.deleteEntry(alias)
        }
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
