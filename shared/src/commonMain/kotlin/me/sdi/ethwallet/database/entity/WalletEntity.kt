package me.sdi.ethwallet.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an Ethereum wallet in the database.
 *
 * @property address The Ethereum address (primary key)
 * @property encryptedPrivateKeyBase64 The private key encrypted using KeyStore and encoded as Base64
 * @property label A user-assigned name for this wallet
 * @property isActive Whether this wallet is currently active
 */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey
    val address: String,
    val encryptedPrivateKeyBase64: String,
    val label: String,
    val isActive: Boolean = false,
)
