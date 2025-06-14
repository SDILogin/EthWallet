package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.database.dao.InMemoryTokenBalanceDao
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

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

    @Before
    fun setUp() {
        inMemoryTokenBalanceDao.clear()
    }

    @Test
    fun `observe returns empty map initially`() = runTest {
        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(
            "Should return empty map when no cached balances exist",
            emptyMap<EthAddress, BigInteger>(),
            result,
        )
    }

    @Test
    fun `forceUpdate saves balances to database and observe returns them`() = runTest {
        // Mock successful balance retrieval
        val ethBalance = BigInteger("1000000000000000000") // 1 ETH
        val usdtBalance = BigInteger("100000000") // 100 USDT
        fakeEthManager.setBalance(
            wallet = testWallet,
            balances = mapOf(
                EthContractAddress to Result.success(ethBalance),
                KnownTokens.USDT.ethAddress to Result.success(usdtBalance),
            ),
        )

        // Force update should fetch and store balances
        ethBalanceProvider.forceUpdate(testWallet)

        // Observe should return the stored balances
        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals("Should return ETH balance", ethBalance, result[EthContractAddress])
        assertEquals(
            "Should return USDT balance",
            usdtBalance,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
        )
        assertEquals("Should return 2 balances", 2, result.size)
    }

    @Test
    fun `forceUpdate handles network failure by using database fallback`() = runTest {
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

        ethBalanceProvider.forceUpdate(testWallet)

        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(
            "Should fallback to previous ETH balance from database",
            previousBalance,
            result[EthContractAddress],
        )
        assertEquals(
            "Should use new token balance",
            tokenBalance,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
        )
    }

    @Test
    fun `forceUpdate handles complete network failure with no database fallback`() = runTest {
        fakeEthManager.setBalance(
            wallet = testWallet,
            balances = mapOf(
                EthContractAddress to Result.failure(RuntimeException("Network error")),
                EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7") to Result.failure(RuntimeException("Network error")),
            ),
        )

        ethBalanceProvider.forceUpdate(testWallet)

        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals("Should default to zero ETH balance", BigInteger.ZERO, result[EthContractAddress])
        assertEquals(
            "Should default to zero token balance",
            BigInteger.ZERO,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
        )
    }
}
