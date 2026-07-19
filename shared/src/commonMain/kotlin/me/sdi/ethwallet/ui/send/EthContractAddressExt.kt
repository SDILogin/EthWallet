package me.sdi.ethwallet.ui.send

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.ERC20_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.ETH_TRANSFER_GAS_UNITS
import java.math.BigInteger

val EthAddress.gasUnits: BigInteger
    get() = if (this == EthContractAddress) ETH_TRANSFER_GAS_UNITS else ERC20_TRANSFER_GAS_UNITS
