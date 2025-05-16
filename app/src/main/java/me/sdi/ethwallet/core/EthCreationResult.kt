package me.sdi.ethwallet.core

import java.math.BigInteger

data class EthCreationResult(
    val address: EthAddress,
    val privateKey: BigInteger,
    val seedPhrase: List<String>,
)
