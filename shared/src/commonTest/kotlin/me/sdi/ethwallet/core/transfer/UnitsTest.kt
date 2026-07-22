package me.sdi.ethwallet.core.transfer

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitsTest {
    @Test
    fun `gwei to wei converts`() {
        assertEquals(
            BigInteger("12350000000"),
            Gwei("12.35".toBigDecimal()).toWei().value,
            "12.35 gwei should be 12_350_000_000 wei",
        )
    }

    @Test
    fun `wei to gwei round trips`() {
        assertEquals(
            BigInteger("12350000000"),
            BigInteger("12350000000").toWei().toGwei().toWei().value,
            "wei should survive a toGwei then toWei round-trip",
        )
    }

    @Test
    fun `wei to gwei strips trailing zeros`() {
        assertEquals(
            "10",
            BigInteger("10000000000").toWei().toGwei().value.toPlainString(),
            "10 gwei should carry no trailing zeros",
        )
    }

    @Test
    fun `sub gwei precision round trips`() {
        assertEquals(
            BigInteger.ONE,
            BigInteger.ONE.toWei().toGwei().toWei().value,
            "1 wei (1E-9 gwei) should round-trip back to 1 wei",
        )
    }

    @Test
    fun `big integer to wei wraps`() {
        assertEquals(
            BigInteger("123"),
            BigInteger("123").toWei().value,
            "BigInteger.toWei should wrap the value unchanged",
        )
    }

    @Test
    fun `big decimal to wei truncates`() {
        assertEquals(
            BigInteger.ONE,
            "1.9".toBigDecimal().toWei().value,
            "BigDecimal.toWei should truncate to a whole wei",
        )
    }

    @Test
    fun `wei to eth converts`() {
        assertEquals(
            0,
            Wei(BigInteger("1000000000000000000")).toEth().value.compareTo(BigDecimal.ONE),
            "1e18 wei should be 1 ETH",
        )
    }

    @Test
    fun `eth to wei converts`() {
        assertEquals(
            BigInteger("1000000000000000000"),
            Eth(BigDecimal.ONE).toWei().value,
            "1 ETH should be 1e18 wei",
        )
    }

    @Test
    fun `int eth builds eth amount`() {
        assertEquals(
            Eth(BigDecimal("2")),
            2.eth,
            "2.eth should build a 2 ETH amount",
        )
    }

    @Test
    fun `double eth avoids binary floating point drift`() {
        assertEquals(
            Eth(BigDecimal("0.1")),
            0.1.eth,
            "0.1.eth should be exactly 0.1 ETH, not the Double-constructor approximation",
        )
    }

    @Test
    fun `negative eth builds negative amount`() {
        assertEquals(
            Eth(BigDecimal("-1")),
            (-1).eth,
            "(-1).eth should build a -1 ETH amount",
        )
    }

    @Test
    fun `eth sugar converts to wei`() {
        assertEquals(
            BigInteger("2000000000000000000"),
            2.eth.toWei().value,
            "2.eth should be 2e18 wei",
        )
    }

    @Test
    fun `string gwei builds gwei amount`() {
        assertEquals(
            Gwei(BigDecimal("12.5")),
            "12.5".gwei,
            "\"12.5\".gwei should build a 12.5 gwei amount",
        )
    }

    @Test
    fun `eth to usd applies rate`() {
        assertEquals(
            0,
            Eth("0.00021".toBigDecimal()).toUsd("2000".toBigDecimal()).value.compareTo("0.42".toBigDecimal()),
            "0.00021 ETH at a 2000 rate should be 0.42 USD",
        )
    }
}
