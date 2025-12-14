package me.sdi.ethwallet.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fiat_rates")
data class FiatRateEntity(
    @PrimaryKey
    @ColumnInfo(name = "token_address")
    val tokenAddress: String,
    @ColumnInfo(name = "rate")
    val rate: String,
    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long,
)
