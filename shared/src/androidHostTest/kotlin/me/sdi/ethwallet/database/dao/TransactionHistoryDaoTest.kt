package me.sdi.ethwallet.database.dao

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.TransactionHistoryEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TransactionHistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TransactionHistoryDao

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.transactionHistoryDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun observe_filters_by_chain_and_wallet_and_orders_pending_first() = runTest {
        val confirmed = entity(hash = "confirmed", timestamp = "2026-09-04T09:00:00Z", status = "Confirmed")
        val pending = entity(hash = "pending", timestamp = null, status = "Pending")
        val older = entity(hash = "older", timestamp = "2026-09-04T08:00:00Z", status = "Confirmed")
        dao.upsertAll(
            listOf(
                older,
                confirmed,
                pending,
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
        val updated = original.copy(timestamp = "2026-09-04T09:00:00Z", status = "Confirmed")

        dao.upsertAll(listOf(original))
        dao.upsertAll(listOf(updated))

        assertEquals(listOf(updated), dao.observe(chainId = 1, walletAddress = WALLET).first())
    }

    private fun entity(
        hash: String,
        chainId: Long = 1,
        walletAddress: String = WALLET,
        timestamp: String? = "2026-09-04T08:00:00Z",
        status: String = "Confirmed",
    ) = TransactionHistoryEntity(
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
