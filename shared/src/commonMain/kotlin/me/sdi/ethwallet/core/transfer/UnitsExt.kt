package me.sdi.ethwallet.core.transfer

import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

fun Gwei.toWei(): Wei = Wei(Convert.toWei(value, Convert.Unit.GWEI).toBigInteger())

fun Wei.toGwei(): Gwei = Gwei(Convert.fromWei(value.toBigDecimal(), Convert.Unit.GWEI).stripTrailingZeros())

fun BigInteger.toWei(): Wei = Wei(this)

fun BigDecimal.toWei(): Wei = Wei(toBigInteger())

fun Wei.toEth(): Eth = Eth(Convert.fromWei(value.toBigDecimal(), Convert.Unit.ETHER))

fun Eth.toWei(): Wei = Convert.toWei(value, Convert.Unit.ETHER).toWei()

fun Eth.toUsd(rate: BigDecimal): Usd = Usd(value.multiply(rate))

val Number.eth: Eth get() = Eth(toString().toBigDecimal())

val String.gwei: Gwei get() = Gwei(toBigDecimal())

val String.wei: Wei get() = Wei(toBigInteger())
