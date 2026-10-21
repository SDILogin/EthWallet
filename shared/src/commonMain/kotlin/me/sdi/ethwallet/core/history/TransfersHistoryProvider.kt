package me.sdi.ethwallet.core.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.transfer.Wei

interface TransfersHistoryProvider {
    suspend fun getTransfers(
        address: EthAddress,
        eth: TransfersPageRequest = TransfersPageRequest.First,
        erc20: TransfersPageRequest = TransfersPageRequest.First,
    ): CombinedTransfersPage
}

sealed interface TransfersPageRequest {
    data object First : TransfersPageRequest

    data class Next(val parameters: Map<String, String>) : TransfersPageRequest

    data object Skip : TransfersPageRequest
}

data class TransfersPage(
    val items: List<Transfer>,
    val nextRequest: TransfersPageRequest,
    // Use the response range before filtering so pages without visible transfers still advance refresh.
    val oldestTimestamp: Long?,
)

data class CombinedTransfersPage(
    val eth: TransfersPage?,
    val erc20: TransfersPage?,
) {
    val items: List<Transfer> get() = eth?.items.orEmpty() + erc20?.items.orEmpty()
}

enum class TransactionStatus {
    Pending,
    Confirmed,
    Failed,
}

data class TransactionFee(val value: Wei, val estimated: Boolean)

sealed interface Transfer {
    val hash: String
    val timestamp: Long
    val from: EthAddress
    val to: EthAddress
    val status: TransactionStatus
    val fee: TransactionFee?

    data class Eth(
        override val hash: String,
        override val timestamp: Long,
        override val from: EthAddress,
        override val to: EthAddress,
        val valueWei: Wei,
        override val status: TransactionStatus,
        override val fee: TransactionFee? = null,
    ) : Transfer

    data class Erc20(
        override val hash: String,
        override val timestamp: Long,
        override val from: EthAddress,
        override val to: EthAddress,
        val logIndex: Int,
        val tokenAddress: EthAddress,
        val tokenSymbol: String?,
        val tokenDecimals: Int,
        val value: String,
        override val status: TransactionStatus = TransactionStatus.Confirmed,
        override val fee: TransactionFee? = null,
    ) : Transfer
}

internal val Transfer.logIndex: Int get() = (this as? Transfer.Erc20)?.logIndex ?: -1

internal val Transfer.identity: Pair<String, Int> get() = hash.lowercase() to logIndex
