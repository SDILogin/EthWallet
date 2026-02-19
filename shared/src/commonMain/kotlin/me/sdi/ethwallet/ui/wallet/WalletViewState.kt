package me.sdi.ethwallet.ui.wallet

import me.sdi.ethwallet.core.EthAddress
import java.math.BigDecimal

/**
 * Represents the state of the wallet screen.
 *
 * @property chainId Active blockchain chain ID.
 * @property totalBalanceUsd The total wallet balance in USD
 * @property totalBalanceEth The total wallet balance in ETH
 * @property favoriteTokens The list of favorite tokens
 * @property isLoading Whether balance data is being loaded
 */
data class WalletViewState(
    val chainId: Long = 0L,
    val totalBalanceUsd: String = "",
    val totalBalanceEth: String = "",
    val favoriteTokens: List<FavoriteToken> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * Represents a favorite token in the wallet.
 *
 * @property address The Ethereum address of the token contract
 * @property name The name of the token
 * @property ticker The ticker symbol of the token
 * @property amount The amount of tokens owned (as a string)
 * @property usdValue The approximate USD value of the tokens (as a string)
 * @property usdValueFormatted The approximate USD value of the tokens (as a string)
 */
data class FavoriteToken(
    val address: EthAddress,
    val name: String,
    val ticker: String,
    val amount: String,
    val usdValue: BigDecimal?,
    val usdValueFormatted: String,
)
