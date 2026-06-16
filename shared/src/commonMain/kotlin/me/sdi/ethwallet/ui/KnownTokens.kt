package me.sdi.ethwallet.ui

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.logo_dai_56
import me.sdi.ethwallet.resources.logo_usdc_56
import me.sdi.ethwallet.resources.logo_usdt_56
import org.jetbrains.compose.resources.DrawableResource

const val ETH_MAINNET_CHAIN_ID = 1L
const val ETH_SEPOLIA_CHAIN_ID = 11155111L

data class KnownToken(
    val symbol: String,
    val ethAddress: EthAddress,
    val tokenName: String,
    val decimals: Int,
    val iconRes: DrawableResource,
)

val usdtOnMainnet = KnownToken(
    "USDT",
    EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7"),
    "Tether USD",
    6,
    Res.drawable.logo_usdt_56,
)
val usdcOnMainnet = KnownToken(
    "USDC",
    EthAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
    "USD Coin",
    6,
    Res.drawable.logo_usdc_56,
)
val daiOnMainnet = KnownToken(
    "DAI",
    EthAddress("0x6B175474E89094C44Da98b954EedeAC495271d0F"),
    "Dai Stablecoin",
    18,
    Res.drawable.logo_dai_56,
)

// Sepolia testnet faucet tokens (Aave v3 reserves / Circle).
val usdtOnSepolia = KnownToken(
    "USDT",
    EthAddress("0xaA8E23Fb1079EA71e0a56F48a2aA51851D8433D0"),
    "Tether USD",
    6,
    Res.drawable.logo_usdt_56,
)
val usdcOnSepolia = KnownToken(
    "USDC",
    EthAddress("0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"),
    "USD Coin",
    6,
    Res.drawable.logo_usdc_56,
)
val daiOnSepolia = KnownToken(
    "DAI",
    EthAddress("0xFF34B3d4Aee8ddCd6F9AFFFB6Fe49bD371b8a357"),
    "Dai Stablecoin",
    18,
    Res.drawable.logo_dai_56,
)

val knownTokens: Map<Long, List<KnownToken>> = mapOf(
    ETH_MAINNET_CHAIN_ID to listOf(usdtOnMainnet, usdcOnMainnet, daiOnMainnet),
    ETH_SEPOLIA_CHAIN_ID to listOf(usdtOnSepolia, usdcOnSepolia, daiOnSepolia),
)

val allKnownTokens: List<KnownToken> = knownTokens.values.flatten()

fun supportedKnownTokens(chainId: Long): List<KnownToken> = knownTokens[chainId].orEmpty()

fun findKnownToken(address: EthAddress): KnownToken? = allKnownTokens.find { it.ethAddress == address }
