package me.sdi.ethwallet.core.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.core.EthAddress

interface HistoryInitializationStore {
    fun observe(chainId: Long, walletAddress: EthAddress): Flow<Boolean>

    suspend fun markInitialized(chainId: Long, walletAddress: EthAddress)

    suspend fun clearWallet(walletAddress: EthAddress)
}

class HistoryInitializationStoreDataStore(
    private val dataStore: DataStore<Preferences>,
) : HistoryInitializationStore {
    override fun observe(chainId: Long, walletAddress: EthAddress): Flow<Boolean> = dataStore.data
        .map { it[key(chainId, walletAddress)] ?: false }
        .distinctUntilChanged()

    override suspend fun markInitialized(chainId: Long, walletAddress: EthAddress) {
        dataStore.edit { it[key(chainId, walletAddress)] = true }
    }

    override suspend fun clearWallet(walletAddress: EthAddress) {
        dataStore.edit { preferences ->
            preferences.asMap().keys.filter { it.name.startsWith(prefix(walletAddress)) }.forEach {
                preferences.remove(it)
            }
        }
    }

    private fun prefix(address: EthAddress) = "history_initialized_${address.toString().lowercase()}_"

    private fun key(chainId: Long, address: EthAddress) = booleanPreferencesKey("${prefix(address)}$chainId")
}
