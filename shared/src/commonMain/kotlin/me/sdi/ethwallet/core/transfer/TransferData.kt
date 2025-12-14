package me.sdi.ethwallet.core.transfer

import me.sdi.ethwallet.core.EthAddress
import java.math.BigInteger

data class TransferData(
    val destinationAddress: EthAddress,
    val amount: BigInteger,
    val contractAddress: EthAddress,
    val gasParams: GasParams = GasParams.Default,
)
