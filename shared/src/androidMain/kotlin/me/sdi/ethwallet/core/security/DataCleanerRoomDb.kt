package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.database.AppDatabase

class DataCleanerRoomDb(
    private val database: AppDatabase,
) : DataCleaner {
    override suspend fun clean() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
