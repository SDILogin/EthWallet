package me.sdi.ethwallet.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity representing an Ethereum wallet in the database.
 *
 * @property address The Ethereum address (primary key)
 * @property encryptedPrivateKeyBase64 The private key encrypted using KeyStore and encoded as Base64
 * @property label A user-assigned name for this wallet
 */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey
    val address: String,
    val encryptedPrivateKeyBase64: String,
    val label: String,
)

/**
 * Table with a single row representing the currently active wallet.
 *
 * Primary key is fixed int = 1.
 * Foreign key is to the address column of the wallets table.
 */
@Entity(
    tableName = "current_wallet",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["address"],
            childColumns = ["address"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CurrentWalletEntity(
    val id: Int = 1,
    val address: String,
)

