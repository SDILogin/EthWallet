package me.sdi.ethwallet.blockchain

import java.math.BigInteger

data class EthCreationResult(
    val address: EthAddress,
    val privateKey: BigInteger,
    val seedPhrase: List<String>,
)
