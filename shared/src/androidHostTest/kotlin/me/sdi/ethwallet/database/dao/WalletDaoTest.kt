package me.sdi.ethwallet.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.CurrentWalletEntity
import me.sdi.ethwallet.database.entity.WalletEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WalletDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var walletDao: WalletDao

    private val firstWallet = WalletEntity(
        address = "0x0000000000000000000000000000000000000001",
        encryptedPrivateKeyBase64 = "encrypted-private-key-1",
        label = "First wallet",
    )
    private val secondWallet = WalletEntity(
        address = "0x0000000000000000000000000000000000000002",
        encryptedPrivateKeyBase64 = "encrypted-private-key-2",
        label = "Second wallet",
    )
    private val updatedFirstWallet = firstWallet.copy(
        encryptedPrivateKeyBase64 = "updated-encrypted-private-key-1",
        label = "Updated first wallet",
    )

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        walletDao = database.walletDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyDatabaseReturnsEmptyWalletList() = runTest {
        assertEquals(emptyList(), walletDao.observeAllWallets().first())
    }

    @Test
    fun emptyDatabaseReturnsNullCurrentWallet() = runTest {
        assertNull(walletDao.currentActiveWallet().first())
    }

    @Test
    fun insertFirstWallet() = runTest {
        walletDao.insertWallet(firstWallet)

        assertEquals(listOf(firstWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun insertFirstWalletMakesItCurrent() = runTest {
        walletDao.insertWallet(firstWallet)

        assertEquals(firstWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun insertSecondWalletKeepsBothWallets() = runTest {
        walletDao.insertWallet(firstWallet)
        walletDao.insertWallet(secondWallet)

        assertEquals(
            setOf(firstWallet, secondWallet),
            walletDao.observeAllWallets().first().toSet(),
        )
    }

    @Test
    fun insertSecondWalletMakesSecondWalletCurrent() = runTest {
        walletDao.insertWallet(firstWallet)
        walletDao.insertWallet(secondWallet)

        assertEquals(secondWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun changeCurrentActiveWallet() = runTest {
        insertTwoWallets()

        walletDao.setActiveWallet(CurrentWalletEntity(address = firstWallet.address))

        assertEquals(firstWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun changingCurrentWalletKeepsAllWalletsUnchanged() = runTest {
        insertTwoWallets()
        val walletsBeforeChangingCurrent = walletDao.observeAllWallets().first().toSet()

        walletDao.setActiveWallet(CurrentWalletEntity(address = firstWallet.address))

        assertEquals(walletsBeforeChangingCurrent, walletDao.observeAllWallets().first().toSet())
    }

    @Test
    fun setNonexistentWalletAsCurrentFailsBecauseOfForeignKey() = runTest {
        walletDao.insertWallet(firstWallet)

        assertFailsWith<SQLiteConstraintException> {
            walletDao.setActiveWallet(
                CurrentWalletEntity(address = "0x0000000000000000000000000000000000009999"),
            )
        }
    }

    @Test
    fun failedCurrentWalletChangePreservesPreviousCurrentWallet() = runTest {
        walletDao.insertWallet(firstWallet)

        assertFailsWith<SQLiteConstraintException> {
            walletDao.setActiveWallet(
                CurrentWalletEntity(address = "0x0000000000000000000000000000000000009999"),
            )
        }

        assertEquals(firstWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun reinsertExistingWalletUpdatesItsLabelAndEncryptedKey() = runTest {
        walletDao.insertWallet(firstWallet)

        walletDao.insertWallet(updatedFirstWallet)

        assertEquals(listOf(updatedFirstWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun reinsertExistingWalletMakesItCurrent() = runTest {
        insertTwoWallets()

        walletDao.insertWallet(updatedFirstWallet)

        assertEquals(updatedFirstWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun deleteInactiveWallet() = runTest {
        insertTwoWallets()

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertEquals(listOf(secondWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun deleteInactiveWalletPreservesCurrentWallet() = runTest {
        insertTwoWallets()

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertEquals(secondWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun deleteCurrentWalletWhileOtherWalletsRemain() = runTest {
        insertTwoWallets()

        walletDao.deleteWalletByAddress(secondWallet.address)

        assertEquals(listOf(firstWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun deletingCurrentWalletClearsCurrentSelection() = runTest {
        insertTwoWallets()

        walletDao.deleteWalletByAddress(secondWallet.address)

        assertNull(walletDao.currentActiveWallet().first())
    }

    @Test
    fun deleteLastWallet() = runTest {
        walletDao.insertWallet(firstWallet)

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertEquals(emptyList(), walletDao.observeAllWallets().first())
    }

    @Test
    fun deletingLastWalletReturnsNullCurrentWallet() = runTest {
        walletDao.insertWallet(firstWallet)

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertNull(walletDao.currentActiveWallet().first())
    }

    @Test
    fun deleteNonexistentWalletDoesNotChangeDatabase() = runTest {
        insertTwoWallets()

        walletDao.deleteWalletByAddress("0x0000000000000000000000000000000000009999")

        assertEquals(
            setOf(firstWallet, secondWallet),
            walletDao.observeAllWallets().first().toSet(),
        )
        assertEquals(secondWallet, walletDao.currentActiveWallet().first())
    }

    @Test
    fun currentWalletTableContainsOnlyOneRowAfterMultipleSelections() = runTest {
        insertTwoWallets()
        walletDao.setActiveWallet(CurrentWalletEntity(address = firstWallet.address))
        walletDao.setActiveWallet(CurrentWalletEntity(address = secondWallet.address))

        assertEquals(1, currentWalletRowCount())
    }

    @Test
    fun observeAllWalletsEmitsAfterInsertion() = runTest {
        val initialEmission = CompletableDeferred<Unit>()
        val insertedWallets = async {
            walletDao
                .observeAllWallets()
                .onEach { initialEmission.complete(Unit) }
                .drop(1)
                .first()
        }
        initialEmission.await()

        walletDao.insertWallet(firstWallet)

        assertEquals(listOf(firstWallet), insertedWallets.await())
    }

    @Test
    fun observeAllWalletsEmitsAfterDeletion() = runTest {
        walletDao.insertWallet(firstWallet)
        val initialEmission = CompletableDeferred<Unit>()
        val remainingWallets = async {
            walletDao
                .observeAllWallets()
                .onEach { initialEmission.complete(Unit) }
                .drop(1)
                .first()
        }
        initialEmission.await()

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertEquals(emptyList(), remainingWallets.await())
    }

    @Test
    fun observeCurrentWalletEmitsAfterSelectionChange() = runTest {
        insertTwoWallets()
        walletDao.setActiveWallet(CurrentWalletEntity(address = firstWallet.address))
        val initialEmission = CompletableDeferred<Unit>()
        val currentWallet = async {
            walletDao
                .currentActiveWallet()
                .onEach { initialEmission.complete(Unit) }
                .drop(1)
                .first()
        }
        initialEmission.await()

        walletDao.setActiveWallet(CurrentWalletEntity(address = secondWallet.address))

        assertEquals(secondWallet, currentWallet.await())
    }

    @Test
    fun observeCurrentWalletEmitsNullAfterCurrentWalletDeletion() = runTest {
        walletDao.insertWallet(firstWallet)
        val initialEmission = CompletableDeferred<Unit>()
        val currentWallet = async {
            walletDao
                .currentActiveWallet()
                .onEach { initialEmission.complete(Unit) }
                .drop(1)
                .first()
        }
        initialEmission.await()

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertNull(currentWallet.await())
    }

    @Test
    fun insertWalletTransactionDoesNotExposeWalletWithoutCurrentSelection() = runTest {
        val initialEmission = CompletableDeferred<Unit>()
        val currentWalletWhenInsertedWalletIsVisible = async {
            walletDao
                .observeAllWallets()
                .onEach { wallets ->
                    if (wallets.isEmpty()) {
                        initialEmission.complete(Unit)
                    }
                }
                .drop(1)
                .first()
                .let { walletDao.currentActiveWallet().first() }
        }
        initialEmission.await()

        walletDao.insertWallet(firstWallet)

        assertEquals(firstWallet, currentWalletWhenInsertedWalletIsVisible.await())
    }

    private suspend fun insertTwoWallets() {
        walletDao.insertWallet(firstWallet)
        walletDao.insertWallet(secondWallet)
    }

    private fun currentWalletRowCount(): Int = database
        .openHelper
        .readableDatabase
        .query("SELECT COUNT(*) FROM current_wallet")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
