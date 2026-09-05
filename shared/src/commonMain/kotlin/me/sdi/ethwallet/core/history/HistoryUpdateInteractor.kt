package me.sdi.ethwallet.core.history

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.sdi.ethwallet.core.EthAddress

interface HistoryUpdateInteractor {
    suspend fun update(walletAddress: EthAddress)
}

class HistoryUpdateInteractorDefault(
    private val historyProvider: TransactionHistoryProvider,
    private val historyRepository: TransactionHistoryRepository,
) : HistoryUpdateInteractor {
    override suspend fun update(walletAddress: EthAddress) {
        val (transactions, tokenTransfers) = coroutineScope {
            val transactions = async { historyProvider.getTransactions(walletAddress).items }
            val tokenTransfers = async { historyProvider.getTokenTransfers(walletAddress).items }
            transactions.await() to tokenTransfers.await()
        }

        historyRepository.upsert(
            walletAddress = walletAddress,
            items = normalizeHistory(transactions, tokenTransfers),
        )
    }
}

internal fun normalizeHistory(
    transactions: List<TransactionHistoryItem>,
    tokenTransfers: List<TokenTransferHistoryItem>,
): List<HistoryItem> {
    val validTokenTransfers = tokenTransfers.mapNotNull { transfer ->
        val logIndex = transfer.logIndex ?: return@mapNotNull null
        HistoryItem.TokenTransfer(
            transactionHash = transfer.transactionHash,
            timestamp = transfer.timestamp,
            from = transfer.from,
            to = transfer.to,
            logIndex = logIndex,
            tokenAddress = transfer.tokenAddress,
            tokenSymbol = transfer.tokenSymbol,
            tokenDecimals = transfer.tokenDecimals,
            value = transfer.value,
        )
    }
    val tokenTransactionHashes = validTokenTransfers.mapTo(mutableSetOf(), HistoryItem::transactionHash)
    val nativeTransfers = transactions
        .filterNot { transaction ->
            transaction.valueWei == "0" &&
                transaction.status == TransactionStatus.Confirmed &&
                transaction.hash in tokenTransactionHashes
        }
        .map { transaction ->
            HistoryItem.NativeTransfer(
                transactionHash = transaction.hash,
                timestamp = transaction.timestamp,
                from = transaction.from,
                to = transaction.to,
                valueWei = transaction.valueWei,
                status = transaction.status,
            )
        }

    return nativeTransfers + validTokenTransfers
}
