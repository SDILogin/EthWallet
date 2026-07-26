package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import java.io.File

class DataCleanerPinFile(private val artifactsFile: File) : DataCleaner {
    override suspend fun clean() = withContext(Dispatchers.IO) {
        if (artifactsFile.exists() && !artifactsFile.delete()) {
            Logger.warn(TAG) { "Failed to delete the PIN artifacts file" }
        }
    }

    private companion object {
        private const val TAG = "DataCleanerPinFile"
    }
}
