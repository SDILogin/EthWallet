package me.sdi.ethwallet.ui.wallet

import androidx.compose.runtime.Immutable
import me.sdi.ethwallet.core.EthAddress
import java.math.BigDecimal

/**
 * Represents the state of the wallet screen.
 *
 * @property chainId Active blockchain chain ID.
 * @property totalBalanceUsd The total wallet balance in USD, or `null` while fiat data is unavailable
 * @property totalBalanceEth The total wallet balance in ETH
 * @property favoriteTokens The list of favorite tokens
 * @property balanceLoadState The current blockchain balance loading state
 * @property fiatLoadState The current fiat-rate loading state
 */
@Immutable
data class WalletViewState(
    val chainId: Long = 0L,
    val walletName: String = "",
    val totalBalanceUsd: String? = null,
    val totalBalanceEth: String = "",
    val favoriteTokens: List<FavoriteToken> = emptyList(),
    val balanceLoadState: WalletBalanceLoadState = WalletBalanceLoadState.Loading,
    val fiatLoadState: WalletFiatLoadState = WalletFiatLoadState.Loading,
    val showP2rIndicator: Boolean = false,
)

enum class WalletBalanceLoadState {
    Loading,
    Content,
    Error,
}

enum class WalletFiatLoadState {
    Loading,
    Available,
    Unavailable,
}

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
