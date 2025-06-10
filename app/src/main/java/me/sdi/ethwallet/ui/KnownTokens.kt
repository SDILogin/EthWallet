package me.sdi.ethwallet.ui

import androidx.annotation.DrawableRes
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress

enum class KnownTokens(
    val ethAddress: EthAddress,
    val tokenName: String,
    val decimals: Int,
    @DrawableRes val icon: Int,
) {
    USDT(
        EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7"),
        "Tether USD",
        6,
        R.drawable.logo_usdt_56,
    ),
    USDC(
        EthAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
        "USD Coin",
        6,
        R.drawable.logo_usdc_56,
    ),
    DAI(
        EthAddress("0x6B175474E89094C44Da98b954EedeAC495271d0F"),
        "Dai Stablecoin",
        18,
        R.drawable.logo_dai_56,
    ),
}
