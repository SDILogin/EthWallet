package me.sdi.ethwallet.ui

import androidx.annotation.DrawableRes
import me.sdi.ethwallet.shared.R

actual val KnownTokens.icon: Int
    @DrawableRes
    get() = when (this) {
        KnownTokens.USDT -> R.drawable.logo_usdt_56
        KnownTokens.USDC -> R.drawable.logo_usdc_56
        KnownTokens.DAI -> R.drawable.logo_dai_56
    }
