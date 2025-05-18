package me.sdi.ethwallet.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey
    val address: String,
    val encryptedPrivateKey: String,
    val label: String,
)
