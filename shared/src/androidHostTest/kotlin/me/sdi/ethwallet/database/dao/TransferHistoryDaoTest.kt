package me.sdi.ethwallet.database.dao

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepositoryRoom
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.TransferHistoryEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class TransferHistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TransferHistoryDao

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.transferHistoryDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun cached_system_eth_logs_are_hidden_but_native_and_real_token_transfers_remain() = runTest {
        val config = ChainConfig("https://rpc.example.test", 1, null, "https://history.example.test")
        val repository = TransferHistoryRepositoryRoom(dao, config)
        val native = entity("native")
        val token = entity("token").copy(
            type = "ERC20",
            logIndex = 1,
            tokenAddress = "0x${"3".repeat(40)}",
            tokenSymbol = "USDC",
            tokenDecimals = 6,
        )
        dao.upsertAll(
            listOf(
                native,
                token,
                token.copy(transactionHash = "system", tokenAddress = "0xfffffffffffffffffffffffffffffffffffffffe"),
                token.copy(transactionHash = "mixed", tokenAddress = "0xffffFFFfFFffffffffffffffFfFFFfffFFFfFFfE"),
            ),
        )
        val wallet = EthAddress(WALLET)
        assertEquals(setOf("native", "token"), repository.observe(wallet).first().map { it.hash }.toSet())
        assertEquals(setOf("native", "token"), repository.read(wallet).map { it.hash }.toSet())
    }

    @Test
    fun observe_filters_by_chain_and_wallet_and_orders_pending_first() = runTest {
        val confirmed = entity(
            hash = "confirmed",
            timestamp = Instant.parse("2026-09-04T09:00:00Z").toEpochMilliseconds(),
            status = "Confirmed",
        )
        val pending = entity(hash = "pending", timestamp = 0L, status = "Pending")
        val older = entity(
            hash = "older",
            timestamp = Instant.parse("2026-09-04T08:00:00Z").toEpochMilliseconds(),
            status = "Confirmed",
        )
        dao.upsertAll(
            listOf(
                older,
                confirmed,
                pending,
                entity(hash = "failed", status = "Failed"),
                entity(hash = "other-chain", chainId = 2),
                entity(hash = "other-wallet", walletAddress = "0x2222222222222222222222222222222222222222"),
            ),
        )

        assertEquals(
            listOf(pending, confirmed, older),
            dao.observe(chainId = 1, walletAddress = WALLET).first(),
        )
    }

    @Test
    fun upsert_replaces_an_existing_history_item() = runTest {
        val original = entity(hash = "same", status = "Pending")
        val updated =
            original.copy(timestamp = Instant.parse("2026-09-04T09:00:00Z").toEpochMilliseconds(), status = "Confirmed")

        dao.upsertAll(listOf(original))
        dao.upsertAll(listOf(updated))

        assertEquals(listOf(updated), dao.observe(chainId = 1, walletAddress = WALLET).first())
    }

    @Test
    fun failed_updates_leave_visible_history_but_remain_in_snapshot() = runTest {
        val pending = entity(hash = "same", status = "Pending")
        dao.upsertAll(listOf(pending))
        val failed = pending.copy(status = "Failed")
        dao.upsertAll(listOf(failed))

        assertEquals(emptyList(), dao.observe(1, WALLET).first())
        assertEquals(listOf(failed), dao.readWalletTransfers(1, WALLET))
    }

    @Test
    fun invalid_cached_record_does_not_hide_valid_history_or_block_snapshot() = runTest {
        val config = ChainConfig("https://rpc.example.test", 1, null, "https://history.example.test")
        val repository = TransferHistoryRepositoryRoom(dao, config)
        dao.upsertAll(listOf(entity("valid"), entity("invalid").copy(type = "Unknown")))
        val wallet = EthAddress(WALLET)
        assertEquals(listOf("valid"), repository.observe(wallet).first().map { it.hash })
        assertEquals(listOf("valid"), repository.read(wallet).map { it.hash })
    }

    @Test
    fun repository_round_trips_fees_and_deduplicates_by_normalized_hash_and_log_index() = runTest {
        val config = ChainConfig("https://rpc.example.test", 1, null, "https://history.example.test")
        val repository = TransferHistoryRepositoryRoom(dao, config)
        val wallet = EthAddress(WALLET)
        val hash = "0x${"A".repeat(64)}"
        val fee = TransactionFee("21000000000000".toBigInteger().toWei(), estimated = true)
        val native = Transfer.Eth(
            hash = hash,
            timestamp = 10,
            from = wallet,
            to = EthAddress("0x${"3".repeat(40)}"),
            valueWei = "1".toBigInteger().toWei(),
            status = TransactionStatus.Pending,
            fee = fee,
        )
        val token = Transfer.Erc20(
            hash = hash,
            timestamp = 20,
            from = wallet,
            to = native.to,
            logIndex = 4,
            tokenAddress = native.to,
            tokenSymbol = "USDC",
            tokenDecimals = 6,
            value = "1500000",
            fee = fee,
        )
        val secondLog = token.copy(logIndex = 5)
        repository.upsert(wallet, listOf(native, token, secondLog))
        val confirmed = native.copy(hash = hash.lowercase(), timestamp = 15, status = TransactionStatus.Confirmed)
        repository.upsert(wallet, listOf(confirmed, token.copy(hash = hash.lowercase())))
        assertEquals(
            listOf(token.copy(hash = hash.lowercase()), secondLog.copy(hash = hash.lowercase()), confirmed),
            repository.observe(wallet).first(),
        )
        val between = native.copy(hash = "0x${"b".repeat(64)}", timestamp = 18, status = TransactionStatus.Confirmed)
        repository.upsert(wallet, listOf(between))
        assertEquals(listOf(20L, 20L, 18L, 15L), repository.observe(wallet).first().map { it.timestamp })
    }

    private fun entity(
        hash: String,
        chainId: Long = 1,
        walletAddress: String = WALLET,
        timestamp: Long = Instant.parse("2026-09-04T08:00:00Z").toEpochMilliseconds(),
        status: String = "Confirmed",
    ) = TransferHistoryEntity(
        chainId = chainId,
        walletAddress = walletAddress,
        transactionHash = hash,
        logIndex = -1,
        type = "NATIVE",
        timestamp = timestamp,
        fromAddress = WALLET,
        toAddress = "0x3333333333333333333333333333333333333333",
        value = "1",
        status = status,
        tokenAddress = null,
        tokenSymbol = null,
        tokenDecimals = null,
    )

    private companion object {
        const val WALLET = "0x1111111111111111111111111111111111111111"
    }
}
