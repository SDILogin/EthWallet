package me.sdi.ethwallet.core.transfer

import java.math.BigDecimal
import java.math.BigInteger

private val WEI_PER_GWEI = BigDecimal.TEN.pow(9)
private val WEI_PER_ETHER = BigDecimal.TEN.pow(18)

fun Gwei.toWei(): Wei = Wei(value.multiply(WEI_PER_GWEI).toBigInteger())

fun Wei.toGwei(): Gwei = Gwei(value.toBigDecimal().divide(WEI_PER_GWEI).stripTrailingZeros())

fun BigInteger.toWei(): Wei = Wei(this)

fun BigDecimal.toWei(): Wei = Wei(toBigInteger())

fun Wei.toEth(): Eth = Eth(value.toBigDecimal().divide(WEI_PER_ETHER))

fun Eth.toWei(): Wei = Wei(value.multiply(WEI_PER_ETHER).toBigInteger())

fun Eth.toUsd(rate: BigDecimal): Usd = Usd(value.multiply(rate))

val Number.eth: Eth get() = Eth(toString().toBigDecimal())

val String.gwei: Gwei get() = Gwei(toBigDecimal())

val String.wei: Wei get() = Wei(toBigInteger())
