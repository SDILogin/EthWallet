package me.sdi.ethwallet.core.transfer

import java.math.BigDecimal
import java.math.BigInteger

val ETH_TRANSFER_GAS_UNITS: BigInteger = BigInteger.valueOf(21_000)

val ERC20_TRANSFER_GAS_UNITS: BigInteger = BigInteger.valueOf(65_000)

@JvmInline
value class Wei(val value: BigInteger)

@JvmInline
value class Gwei(val value: BigDecimal)

@JvmInline
value class Eth(val value: BigDecimal)

@JvmInline
value class Usd(val value: BigDecimal)
