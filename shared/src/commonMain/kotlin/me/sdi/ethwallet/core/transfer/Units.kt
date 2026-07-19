package me.sdi.ethwallet.core.transfer

import java.math.BigDecimal
import java.math.BigInteger

val ETH_TRANSFER_GAS_UNITS: BigInteger = BigInteger.valueOf(21_000)

/**
 * Gas-unit basis used only for fee display. This 65_000 estimate is NOT the reserved transaction
 * limit the interactor signs with (that is DefaultGasProvider.GAS_LIMIT).
 */
val ERC20_TRANSFER_GAS_UNITS: BigInteger = BigInteger.valueOf(65_000)

@JvmInline
value class Wei(val value: BigInteger)

@JvmInline
value class Gwei(val value: BigDecimal)

@JvmInline
value class Eth(val value: BigDecimal)

@JvmInline
value class Usd(val value: BigDecimal)
