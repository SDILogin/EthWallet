package me.sdi.ethwallet.core.history

import me.sdi.ethwallet.core.EthAddress

interface TransactionHistoryProvider {
    suspend fun getTransactions(
        address: EthAddress,
        cursor: TransactionHistoryCursor? = null,
    ): TransactionHistoryPage<TransactionHistoryItem>

    suspend fun getTokenTransfers(
        address: EthAddress,
        cursor: TransactionHistoryCursor? = null,
    ): TransactionHistoryPage<TokenTransferHistoryItem>
}

data class TransactionHistoryPage<out T>(
    val items: List<T>,
    val nextCursor: TransactionHistoryCursor?,
)

interface TransactionHistoryCursor

data class TransactionHistoryItem(
    val hash: String,
    val timestamp: String?,
    val blockNumber: Long?,
    val from: EthAddress,
    val to: EthAddress?,
    val valueWei: String,
    val status: TransactionStatus,
)

data class TokenTransferHistoryItem(
    val transactionHash: String,
    val logIndex: Int?,
    val timestamp: String,
    val from: EthAddress,
    val to: EthAddress,
    val tokenAddress: EthAddress,
    val tokenSymbol: String?,
    val tokenDecimals: Int?,
    val value: String,
)

enum class TransactionStatus {
    Pending,
    Confirmed,
    Failed,
}

sealed interface HistoryItem {
    val transactionHash: String
    val timestamp: String?
    val from: EthAddress
    val to: EthAddress?

    data class NativeTransfer(
        override val transactionHash: String,
        override val timestamp: String?,
        override val from: EthAddress,
        override val to: EthAddress?,
        val valueWei: String,
        val status: TransactionStatus,
    ) : HistoryItem

    data class TokenTransfer(
        override val transactionHash: String,
        override val timestamp: String,
        override val from: EthAddress,
        override val to: EthAddress,
        val logIndex: Int,
        val tokenAddress: EthAddress,
        val tokenSymbol: String?,
        val tokenDecimals: Int?,
        val value: String,
    ) : HistoryItem
}
