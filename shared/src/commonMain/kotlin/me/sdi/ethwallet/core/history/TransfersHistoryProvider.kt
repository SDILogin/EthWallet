package me.sdi.ethwallet.core.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.transfer.Wei
import java.math.BigInteger

interface TransfersHistoryProvider {
    suspend fun getTransfers(
        address: EthAddress,
        eth: TransfersPageRequest = TransfersPageRequest.First,
        erc20: TransfersPageRequest = TransfersPageRequest.First,
    ): CombinedTransfersPage

    /** Returns null when the API does not know the transaction yet. */
    suspend fun getTransaction(hash: String): TransactionSummary?
}

data class TransactionSummary(val status: TransactionStatus, val fee: TransactionFee?)

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

internal const val UNKNOWN_LOG_INDEX = -1

/** Builds a sent transfer that is not mined yet. A null [tokenAddress] means ETH. */
fun pendingTransfer(
    hash: String,
    timestamp: Long,
    from: EthAddress,
    to: EthAddress,
    amount: BigInteger,
    estimatedFee: Wei,
    tokenAddress: EthAddress?,
    tokenSymbol: String?,
    tokenDecimals: Int?,
): Transfer {
    val fee = TransactionFee(estimatedFee, estimated = true)
    return if (tokenAddress == null) {
        Transfer.Eth(
            hash = hash,
            timestamp = timestamp,
            from = from,
            to = to,
            valueWei = Wei(amount),
            status = TransactionStatus.Pending,
            fee = fee,
        )
    } else {
        Transfer.Erc20(
            hash = hash,
            timestamp = timestamp,
            from = from,
            to = to,
            logIndex = UNKNOWN_LOG_INDEX,
            tokenAddress = tokenAddress,
            tokenSymbol = tokenSymbol,
            tokenDecimals = checkNotNull(tokenDecimals) { "A token transfer has decimals" },
            value = amount.toString(),
            status = TransactionStatus.Pending,
            fee = fee,
        )
    }
}

internal val Transfer.logIndex: Int get() = (this as? Transfer.Erc20)?.logIndex ?: UNKNOWN_LOG_INDEX

internal val Transfer.identity: Pair<String, Int> get() = hash.lowercase() to logIndex

// EIP-7708 emits native ETH transfer logs from this address using the ERC-20 event format.
internal const val ETH_TRANSFER_LOG_ADDRESS = "0xfffffffffffffffffffffffffffffffffffffffe"
