package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryInitializationStore

class HistoryInitializationStoreInMemory : HistoryInitializationStore {
    private val observableStorage = MutableStateFlow<Map<Pair<Long, EthAddress>, Boolean>>(emptyMap())

    override fun observe(
        chainId: Long,
        walletAddress: EthAddress,
    ): Flow<Boolean> = observableStorage.map { storage -> storage[chainId to walletAddress] ?: false }

    override suspend fun markInitialized(chainId: Long, walletAddress: EthAddress) {
        observableStorage.update { storage ->
            val key = chainId to walletAddress
            val newEntry = key to true
            storage + newEntry
        }
    }

    override suspend fun clearWallet(walletAddress: EthAddress) {
        observableStorage.update { storage ->
            storage.filterNot { (key, _) -> key.second == walletAddress }
        }
    }
}
