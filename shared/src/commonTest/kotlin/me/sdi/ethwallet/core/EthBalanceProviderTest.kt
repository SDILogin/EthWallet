package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.database.dao.InMemoryTokenBalanceDao
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EthBalanceProviderTest {
    private val fakeEthManager = object : EthManager {
        private val balance = mutableMapOf<EthAddress, Map<EthAddress, Result<BigInteger>>>()

        override suspend fun getBalance(address: EthAddress) = balance.getValue(address)

        override suspend fun fetchFeeSuggestion() = throw NotImplementedError()

        override suspend fun sendTransfer(
            privateKey: BigInteger,
            contractAddress: EthAddress,
            destinationAddress: EthAddress,
            amount: BigInteger,
            gasUnits: BigInteger,
            maxPriorityFeePerGas: BigInteger,
            maxFeePerGas: BigInteger,
        ) = throw NotImplementedError()

        override fun createWallet() = throw NotImplementedError()

        override fun importWallet(mnemonic: String) = throw NotImplementedError()

        fun setBalance(address: EthAddress, balances: Map<EthAddress, Result<BigInteger>>) {
            balance[address] = balances
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
        val ethBalance = BigInteger("1000000000000000000")
        val usdtBalance = BigInteger("100000000")
        fakeEthManager.setBalance(
            address = testWalletAddress,
            balances = mapOf(
                EthContractAddress to Result.success(ethBalance),
                usdtOnMainnet.ethAddress to Result.success(usdtBalance),
            ),
        )

        ethBalanceProvider.forceUpdate(testWalletAddress)

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
        val previousBalance = BigInteger("500000000000000000")
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

        val tokenBalance = BigInteger("50000000")
        fakeEthManager.setBalance(
            address = testWalletAddress,
            balances = mapOf(
                EthContractAddress to Result.failure(RuntimeException("Network error")),
                EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7") to Result.success(tokenBalance),
            ),
        )

        ethBalanceProvider.forceUpdate(testWalletAddress)

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
            address = testWalletAddress,
            balances = mapOf(
                EthContractAddress to Result.failure(RuntimeException("Network error")),
                EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7") to Result.failure(RuntimeException("Network error")),
            ),
        )

        ethBalanceProvider.forceUpdate(testWalletAddress)

        val result = ethBalanceProvider.observe(testWalletAddress).first()

        assertEquals(BigInteger.ZERO, result[EthContractAddress], "Should default to zero ETH balance")
        assertEquals(
            BigInteger.ZERO,
            result[EthAddress("0xdAC17F958D2ee523a2206206994597C13D831ec7")],
            "Should default to zero token balance",
        )
    }
}
