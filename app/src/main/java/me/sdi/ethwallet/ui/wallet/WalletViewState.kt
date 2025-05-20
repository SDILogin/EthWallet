package me.sdi.ethwallet.ui.wallet

/**
 * Represents the state of the wallet screen.
 *
 * @property totalBalanceUsd The total wallet balance in USD
 * @property totalBalanceEth The total wallet balance in ETH
 * @property favoriteTokens The list of favorite tokens
 */
data class WalletViewState(
    val totalBalanceUsd: String = "",
    val totalBalanceEth: String = "",
    val favoriteTokens: List<FavoriteToken> = emptyList(),
)

/**
 * Represents a favorite token in the wallet.
 *
 * @property address The Ethereum address of the token contract
 * @property name The name of the token
 * @property ticker The ticker symbol of the token
 * @property amount The amount of tokens owned (as a string)
 * @property usdValue The approximate USD value of the tokens (as a string)
 */
data class FavoriteToken(
    val address: String,
    val name: String,
    val ticker: String,
    val amount: String,
    val usdValue: String,
) {
    /**
     * Returns the balance of the token formatted for display.
     */
    val formattedBalance: String
        get() = "$amount $ticker"

    /**
     * Returns the first character of the ticker for display on the token icon.
     */
    val iconText: String
        get() = when (ticker) {
            "USDT" -> "Ƭ"
            "USDC" -> "$"
            "DAI" -> "◈"
            else -> ticker.firstOrNull()?.toString() ?: ""
        }

    /**
     * Returns the background color for the token icon.
     */
    val iconBackground: Long
        get() = when (ticker) {
            "USDT" -> 0xFF26A17B
            "USDC" -> 0xFF2775CA
            "DAI" -> 0xFFF5AC37
            "ETH" -> 0xFF627EEA
            "BTC" -> 0xFFF7931A
            "LINK" -> 0xFF2A5ADA
            else -> 0xFF808080 // Default gray color
        }
}
