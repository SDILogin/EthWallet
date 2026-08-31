package me.sdi.ethwallet.core

import me.sdi.ethwallet.kotlin.PrivateKey

data class EthCreationResult(
    val address: EthAddress,
    val privateKey: PrivateKey,
    val seedPhrase: List<String>,
)
