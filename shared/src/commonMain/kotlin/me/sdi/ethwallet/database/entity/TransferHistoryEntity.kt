package me.sdi.ethwallet.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "transaction_history",
    primaryKeys = ["chain_id", "wallet_address", "transaction_hash", "log_index"],
    indices = [Index(value = ["chain_id", "wallet_address"])],
)
data class TransferHistoryEntity(
    @ColumnInfo(name = "chain_id") val chainId: Long,
    @ColumnInfo(name = "wallet_address") val walletAddress: String,
    @ColumnInfo(name = "transaction_hash") val transactionHash: String,
    @ColumnInfo(name = "log_index") val logIndex: Int,
    val type: String,
    val timestamp: Long,
    @ColumnInfo(name = "from_address") val fromAddress: String,
    @ColumnInfo(name = "to_address") val toAddress: String?,
    val value: String,
    val status: String,
    @ColumnInfo(name = "fee_wei") val feeWei: String? = null,
    @ColumnInfo(name = "fee_estimated") val feeEstimated: Boolean = false,
    @ColumnInfo(name = "token_address") val tokenAddress: String?,
    @ColumnInfo(name = "token_symbol") val tokenSymbol: String?,
    @ColumnInfo(name = "token_decimals") val tokenDecimals: Int?,
)
