package me.sdi.ethwallet.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Represents a cached balance for a specific wallet and token.
 *
 * For example, if a user has 1000 USDT in their wallet,
 * this entity would store the balance as:
 * ```
 * walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
 * tokenAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7",
 * balance = "1000000000" // 1000 USDT. 6 decimals for USDT
 * ```
 *
 * For ERC-20 tokens, the balance is stored in the smallest unit (wei for ETH, or token-specific smallest unit).
 *
 * @param walletAddress The address of the wallet holding the token balance. There may be multiple
 * addresses for the same user, such as a main wallet and multiple sub-wallets.
 * @param tokenAddress The address of the token contract. This is the unique identifier for the
 * token on the Ethereum blockchain. For ETH, this value will be empty.
 * @param balance The balance of the token in the smallest unit (e.g., wei for ETH, or token-specific smallest unit).
 * @param timestamp The timestamp when the balance was last updated, in milliseconds since epoch.
 */
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
