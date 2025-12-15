package me.sdi.ethwallet.ui

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.logo_dai_56
import me.sdi.ethwallet.resources.logo_usdc_56
import me.sdi.ethwallet.resources.logo_usdt_56
import org.jetbrains.compose.resources.DrawableResource

enum class KnownTokens(
    val ethAddress: EthAddress,
    val tokenName: String,
    val decimals: Int,
) {
    USDT(
        EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7"),
        "Tether USD",
        6,
    ),
    USDC(
        EthAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
        "USD Coin",
        6,
    ),
    DAI(
        EthAddress("0x6B175474E89094C44Da98b954EedeAC495271d0F"),
        "Dai Stablecoin",
        18,
    ),
}

val KnownTokens.icon: DrawableResource
    get() = when (this) {
        KnownTokens.USDT -> Res.drawable.logo_usdt_56
        KnownTokens.USDC -> Res.drawable.logo_usdc_56
        KnownTokens.DAI -> Res.drawable.logo_dai_56
    }
