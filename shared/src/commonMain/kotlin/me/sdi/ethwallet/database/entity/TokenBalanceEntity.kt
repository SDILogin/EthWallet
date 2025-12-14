package me.sdi.ethwallet.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "token_balances",
    primaryKeys = ["wallet_address", "token_address"],
)
data class TokenBalanceEntity(
    @ColumnInfo(name = "wallet_address")
    val walletAddress: String,
    @ColumnInfo(name = "token_address")
    val tokenAddress: String,
    @ColumnInfo(name = "balance")
    val balance: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)

fun createTokenBalanceEntity(
    walletAddress: String,
    tokenAddress: String,
    balance: String,
    timestamp: Long,
): TokenBalanceEntity = TokenBalanceEntity(walletAddress, tokenAddress, balance, timestamp)
