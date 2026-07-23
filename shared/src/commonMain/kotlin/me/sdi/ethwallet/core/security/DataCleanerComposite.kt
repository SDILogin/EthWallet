package me.sdi.ethwallet.core.security

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.sdi.ethwallet.Logger

class DataCleanerComposite(private val dataCleaners: List<DataCleaner>) : DataCleaner {
    override suspend fun clean() = supervisorScope {
        val loggerCoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
            Logger.warn(TAG, error) { "User data cleanup failed" }
        }

        dataCleaners.map { dataCleaner ->
            launch(loggerCoroutineExceptionHandler) {
                dataCleaner.clean()
            }
        }.joinAll()
    }

    private companion object {
        private const val TAG = "DataCleanerUserData"
    }
}
