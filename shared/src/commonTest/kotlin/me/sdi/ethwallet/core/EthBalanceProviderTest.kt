package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.database.dao.InMemoryTokenBalanceDao
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EthBalanceProviderTest {
    private val fakeEthManager = object : EthManager {
        private val balance = mutableMapOf<EthWallet, Map<EthAddress, Result<BigInteger>>>()

        override suspend fun getBalance(wallet: EthWallet) = balance.getValue(wallet)

        fun setBalance(wallet: EthWallet, balances: Map<EthAddress, Result<BigInteger>>) {
            balance[wallet] = balances
        }
    }
    private val inMemoryTokenBalanceDao = InMemoryTokenBalanceDao()
    private val currentTime = 1000000L
    private val ethBalanceProvider = EthBalanceProvider(
        ethManager = fakeEthManager,
        tokenBalanceDao = inMemoryTokenBalanceDao,
        now = { currentTime },
    )

    private val testWalletAddress = EthAddress("0x1234567890abcdef1234567890abcdef12345678")
    private val testWallet = EthWallet(label = "Test Wallet", address = testWalletAddress)

    @BeforeTest
    fun setUp() {
        inMemoryTokenBalanceDao.clear()
    }

    @Test
    fun observe_returns_empty_map_initially() = runTest {
        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(
            emptyMap<EthAddress, BigInteger>(),
            result,
            "Should return empty map when no cached balances exist",
        )
    }

    @Test
    fun forceUpdate_saves_balances_to_database_and_observe_returns_them() = runTest {
        // Mock successful balance retrieval
        val ethBalance = BigInteger("1000000000000000000") // 1 ETH
        val usdtBalance = BigInteger("100000000") // 100 USDT
        fakeEthManager.setBalance(
            wallet = testWallet,
            balances = mapOf(
                EthContractAddress to Result.success(ethBalance),
                usdtOnMainnet.ethAddress to Result.success(usdtBalance),
            ),
        )

        // Force update should fetch and store balances
        ethBalanceProvider.forceUpdate(testWallet)

        // Observe should return the stored balances
        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(ethBalance, result[EthContractAddress], "Should return ETH balance")
        assertEquals(
            usdtBalance,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
            "Should return USDT balance",
        )
        assertEquals(2, result.size, "Should return 2 balances")
    }

    @Test
    fun forceUpdate_handles_network_failure_by_using_database_fallback() = runTest {
        // Pre-populate database with some balance
        val previousBalance = BigInteger("500000000000000000") // 0.5 ETH
        inMemoryTokenBalanceDao.insertAll(
            listOf(
                TokenBalanceEntity(
                    walletAddress = testWalletAddress.toString(),
                    tokenAddress = EthContractAddress.toString(),
                    balance = previousBalance.toString(),
                    timestamp = currentTime - 10000,
                ),
            ),
        )

        val tokenBalance = BigInteger("50000000") // 50 USDT
        fakeEthManager.setBalance(
            wallet = testWallet,
            balances = mapOf(
                EthContractAddress to Result.failure(RuntimeException("Network error")),
                EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7") to Result.success(tokenBalance),
            ),
        )

        // Force update should run
        ethBalanceProvider.forceUpdate(testWallet)

        // Observe should fall back
        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(
            previousBalance,
            result[EthContractAddress],
            "Should fallback to previous ETH balance from database",
        )
        assertEquals(
            tokenBalance,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
            "Should use new token balance",
        )
    }

    @Test
    fun forceUpdate_handles_complete_network_failure_with_no_database_fallback() = runTest {
        fakeEthManager.setBalance(
            wallet = testWallet,
            balances = mapOf(
                EthContractAddress to Result.failure(RuntimeException("Network error")),
                EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7") to Result.failure(RuntimeException("Network error")),
            ),
        )

        ethBalanceProvider.forceUpdate(testWallet)

        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(BigInteger.ZERO, result[EthContractAddress], "Should default to zero ETH balance")
        assertEquals(
            BigInteger.ZERO,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
            "Should default to zero token balance",
        )
    }
}
