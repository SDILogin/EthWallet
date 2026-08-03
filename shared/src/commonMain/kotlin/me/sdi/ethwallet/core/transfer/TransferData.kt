package me.sdi.ethwallet.core.transfer

import me.sdi.ethwallet.core.EthAddress
import java.math.BigInteger

data class TransferData(
    val senderAddress: EthAddress,
    val destinationAddress: EthAddress,
    val amount: BigInteger,
    val contractAddress: EthAddress,
    val maxFeePerGas: Wei,
    val maxPriorityFeePerGas: Wei,
    val gasUnits: BigInteger,
)
