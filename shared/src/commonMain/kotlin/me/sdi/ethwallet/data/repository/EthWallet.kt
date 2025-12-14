package me.sdi.ethwallet.data.repository

import me.sdi.ethwallet.core.EthAddress

data class EthWallet(
    val label: String,
    val address: EthAddress,
)
