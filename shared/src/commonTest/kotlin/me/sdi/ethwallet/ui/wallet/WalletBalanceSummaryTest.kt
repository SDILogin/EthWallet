package me.sdi.ethwallet.ui.wallet

import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.ETH_MAINNET_CHAIN_ID
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdcOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WalletBalanceSummaryTest {
    @Test
    fun depegChangesFiatValueWithoutChangingTokenAmounts() {
        val balances = mapOf(
            EthContractAddress to BigInteger.TEN.pow(18),
            usdtOnMainnet.ethAddress to BigInteger("100000000"),
            usdcOnMainnet.ethAddress to BigInteger("50000000"),
            daiOnMainnet.ethAddress to BigInteger("200000000000000000000"),
        )
        val rates = mapOf(
            EthContractAddress to BigDecimal("2000"),
            usdtOnMainnet.ethAddress to BigDecimal("0.98"),
            usdcOnMainnet.ethAddress to BigDecimal("0.87"),
            daiOnMainnet.ethAddress to BigDecimal("1.02"),
        )
        val summary = mapWalletBalance(balances, rates, ETH_MAINNET_CHAIN_ID)
        assertEquals(listOf("100", "50", "200"), summary.favoriteTokens.map { it.amount })
        assertEquals(listOf("$98.00", "$43.50", "$204.00"), summary.favoriteTokens.map { it.usdValueFormatted })
        assertEquals("$2,345.50", summary.totalBalanceUsd)

        val repriced = mapWalletBalance(balances, rates + (usdcOnMainnet.ethAddress to BigDecimal("0.5")), ETH_MAINNET_CHAIN_ID)
        assertEquals(summary.favoriteTokens.map { it.amount }, repriced.favoriteTokens.map { it.amount })
        assertEquals("$25.00", repriced.favoriteTokens[1].usdValueFormatted)
        assertEquals("$2,327.00", repriced.totalBalanceUsd)
    }

    @Test
    fun missingQuoteKeepsTokenAmountAndLeavesFiatUnavailable() {
        val summary = mapWalletBalance(
            mapOf(usdcOnMainnet.ethAddress to BigInteger("100123456")),
            mapOf(EthContractAddress to BigDecimal("2000")),
            ETH_MAINNET_CHAIN_ID,
        )
        val usdc = summary.favoriteTokens.first { it.address == usdcOnMainnet.ethAddress }
        assertEquals("100.12", usdc.amount)
        assertNull(usdc.usdValue)
        assertNull(summary.totalBalanceUsd)
    }
}
