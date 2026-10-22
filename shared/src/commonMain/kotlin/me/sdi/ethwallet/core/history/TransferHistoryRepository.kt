package me.sdi.ethwallet.core.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.database.dao.TransferHistoryDao
import me.sdi.ethwallet.database.entity.TransferHistoryEntity

interface TransferHistoryRepository {
    fun observe(walletAddress: EthAddress): Flow<List<Transfer>>

    fun observe(walletAddress: EthAddress, trxHash: String, logIndex: Int): Flow<Transfer>

    suspend fun read(walletAddress: EthAddress): List<Transfer>

    suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>)
}

class TransferHistoryRepositoryRoom(
    private val transferHistoryDao: TransferHistoryDao,
    private val chainConfig: ChainConfig,
) : TransferHistoryRepository {
    override fun observe(walletAddress: EthAddress): Flow<List<Transfer>> =
        transferHistoryDao.observe(chainConfig.chainId, walletAddress.normalized())
            .map { entities -> entities.mapNotNull(TransferHistoryEntity::toTransferOrNull) }

    override fun observe(walletAddress: EthAddress, trxHash: String, logIndex: Int): Flow<Transfer> {
        return transferHistoryDao
            .observe(chainConfig.chainId, walletAddress.normalized(), trxHash.lowercase(), logIndex)
            .mapNotNull { it.toTransferOrNull() }
    }

    override suspend fun read(walletAddress: EthAddress): List<Transfer> =
        transferHistoryDao.readWalletTransfers(
            chainConfig.chainId,
            walletAddress.normalized(),
        ).mapNotNull(TransferHistoryEntity::toTransferOrNull)

    override suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>) {
        transferHistoryDao.upsertAll(
            items.map { it.toEntity(chainConfig.chainId, walletAddress) },
        )
    }
}

private fun EthAddress.normalized() = toString().lowercase()

private fun Transfer.toEntity(chainId: Long, walletAddress: EthAddress) = TransferHistoryEntity(
    chainId = chainId,
    walletAddress = walletAddress.normalized(),
    transactionHash = hash.lowercase(),
    logIndex = logIndex,
    type = if (this is Transfer.Eth) "NATIVE" else "ERC20",
    timestamp = timestamp,
    fromAddress = from.toString(),
    toAddress = to.toString(),
    value = when (this) {
        is Transfer.Eth -> valueWei.value.toString()
        is Transfer.Erc20 -> value
    },
    status = status.name,
    feeWei = fee?.value?.value?.toString(),
    feeEstimated = fee?.estimated ?: false,
    tokenAddress = (this as? Transfer.Erc20)?.tokenAddress?.toString(),
    tokenSymbol = (this as? Transfer.Erc20)?.tokenSymbol,
    tokenDecimals = (this as? Transfer.Erc20)?.tokenDecimals,
)

private fun TransferHistoryEntity.toTransferOrNull(): Transfer? = try {
    toTransfer()
} catch (e: Exception) {
    if (e is CancellationException) throw e
    Logger.warn("TransferHistoryEntity", e) { "Failed to decode cached history record" }
    null
}

private fun TransferHistoryEntity.toTransfer(): Transfer {
    val fee = feeWei?.let { TransactionFee(it.toBigInteger().toWei(), feeEstimated) }
    val status = TransactionStatus.valueOf(status)
    return when (type) {
        "NATIVE" -> Transfer.Eth(
            hash = transactionHash,
            timestamp = timestamp,
            from = EthAddress(fromAddress),
            to = EthAddress(requireNotNull(toAddress)),
            valueWei = value.toBigInteger().toWei(),
            status = status,
            fee = fee,
        )
        "ERC20" -> Transfer.Erc20(
            hash = transactionHash,
            timestamp = timestamp,
            from = EthAddress(fromAddress),
            to = EthAddress(requireNotNull(toAddress)),
            logIndex = logIndex,
            tokenAddress = EthAddress(requireNotNull(tokenAddress)),
            tokenSymbol = tokenSymbol,
            tokenDecimals = requireNotNull(tokenDecimals),
            value = value,
            status = status,
            fee = fee,
        )
        else -> error("Unsupported transfer type: $type")
    }
}
