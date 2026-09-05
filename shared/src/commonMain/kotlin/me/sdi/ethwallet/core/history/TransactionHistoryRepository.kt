package me.sdi.ethwallet.core.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.database.dao.TransactionHistoryDao
import me.sdi.ethwallet.database.entity.TransactionHistoryEntity

interface TransactionHistoryRepository {
    fun observe(walletAddress: EthAddress): Flow<List<HistoryItem>>

    suspend fun upsert(walletAddress: EthAddress, items: List<HistoryItem>)
}

class TransactionHistoryRepositoryRoom(
    private val transactionHistoryDao: TransactionHistoryDao,
    private val chainConfig: ChainConfig,
) : TransactionHistoryRepository {
    override fun observe(walletAddress: EthAddress): Flow<List<HistoryItem>> =
        transactionHistoryDao
            .observe(chainConfig.chainId, walletAddress.toString())
            .map { entities -> entities.map(TransactionHistoryEntity::toHistoryItem) }

    override suspend fun upsert(walletAddress: EthAddress, items: List<HistoryItem>) {
        transactionHistoryDao.upsertAll(
            items.map { item -> item.toEntity(chainConfig.chainId, walletAddress) },
        )
    }
}

private const val NATIVE_LOG_INDEX = -1
private const val TYPE_NATIVE = "NATIVE"
private const val TYPE_ERC20 = "ERC20"

private fun HistoryItem.toEntity(chainId: Long, walletAddress: EthAddress): TransactionHistoryEntity = when (this) {
    is HistoryItem.NativeTransfer -> TransactionHistoryEntity(
        chainId = chainId,
        walletAddress = walletAddress.toString(),
        transactionHash = transactionHash,
        logIndex = NATIVE_LOG_INDEX,
        type = TYPE_NATIVE,
        timestamp = timestamp,
        fromAddress = from.toString(),
        toAddress = to?.toString(),
        value = valueWei,
        status = status.name,
        tokenAddress = null,
        tokenSymbol = null,
        tokenDecimals = null,
    )
    is HistoryItem.TokenTransfer -> TransactionHistoryEntity(
        chainId = chainId,
        walletAddress = walletAddress.toString(),
        transactionHash = transactionHash,
        logIndex = logIndex,
        type = TYPE_ERC20,
        timestamp = timestamp,
        fromAddress = from.toString(),
        toAddress = to.toString(),
        value = value,
        status = TransactionStatus.Confirmed.name,
        tokenAddress = tokenAddress.toString(),
        tokenSymbol = tokenSymbol,
        tokenDecimals = tokenDecimals,
    )
}

private fun TransactionHistoryEntity.toHistoryItem(): HistoryItem = when (type) {
    TYPE_NATIVE -> HistoryItem.NativeTransfer(
        transactionHash = transactionHash,
        timestamp = timestamp,
        from = EthAddress(fromAddress),
        to = toAddress?.let(::EthAddress),
        valueWei = value,
        status = TransactionStatus.valueOf(requireNotNull(status)),
    )
    TYPE_ERC20 -> HistoryItem.TokenTransfer(
        transactionHash = transactionHash,
        timestamp = requireNotNull(timestamp),
        from = EthAddress(fromAddress),
        to = EthAddress(requireNotNull(toAddress)),
        logIndex = logIndex,
        tokenAddress = EthAddress(requireNotNull(tokenAddress)),
        tokenSymbol = tokenSymbol,
        tokenDecimals = tokenDecimals,
        value = value,
    )
    else -> error("Unsupported transaction history type: $type")
}
