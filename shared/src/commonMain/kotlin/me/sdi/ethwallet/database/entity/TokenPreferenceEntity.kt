package me.sdi.ethwallet.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "token_preferences",
    primaryKeys = ["chain_id", "wallet_address", "token_address"],
)
data class TokenPreferenceEntity(
    @ColumnInfo(name = "chain_id")
    val chainId: Long,
    @ColumnInfo(name = "wallet_address")
    val walletAddress: String,
    @ColumnInfo(name = "token_address")
    val tokenAddress: String,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "visible")
    val visible: Boolean,
)
