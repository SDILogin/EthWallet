package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.core.history.identity

class TransferHistoryRepositoryInMemory : TransferHistoryRepository {
    val rows = MutableStateFlow<List<Pair<EthAddress, Transfer>>>(emptyList())

    override fun observe(walletAddress: EthAddress): Flow<List<Transfer>> =
        rows.map { items -> items.filter { it.first == walletAddress }.map { it.second } }

    override fun observe(trxHash: String): Flow<Transfer?> = rows.map { items ->
        items.map { it.second }
            .filter { it.hash.equals(trxHash, ignoreCase = true) }
            .minWithOrNull(compareBy({ it.identity.second }, { it.fee == null }))
    }

    override suspend fun read(walletAddress: EthAddress): List<Transfer> =
        rows.value.filter { it.first == walletAddress }.map { it.second }

    override suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>) {
        val keys = items.mapTo(mutableSetOf()) { walletAddress to it.identity }
        rows.update { current ->
            current.filterNot { (it.first to it.second.identity) in keys } + items.map { walletAddress to it }
        }
    }

    override suspend fun update(trxHash: String, status: TransactionStatus, fee: TransactionFee?) {
        rows.update { current ->
            current.map { (wallet, transfer) ->
                if (transfer.hash.equals(trxHash, ignoreCase = true)) {
                    wallet to when (transfer) {
                        is Transfer.Eth -> transfer.copy(status = status, fee = transfer.fee?.let { fee ?: it })
                        is Transfer.Erc20 -> transfer.copy(status = status, fee = transfer.fee?.let { fee ?: it })
                    }
                } else {
                    wallet to transfer
                }
            }
        }
    }

    override suspend fun delete(walletAddress: EthAddress, items: List<Transfer>) {
        val keys = items.mapTo(mutableSetOf()) { walletAddress to it.identity }
        rows.update { current -> current.filterNot { (it.first to it.second.identity) in keys } }
    }
}
