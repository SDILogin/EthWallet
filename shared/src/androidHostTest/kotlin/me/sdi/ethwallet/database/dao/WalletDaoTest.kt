package me.sdi.ethwallet.database.dao

import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.WalletEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun insertWalletPersistsIt() = runTest {
        walletDao.insertWallet(firstWallet)

        assertEquals(listOf(firstWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun reinsertWalletUpdatesItsLabelAndEncryptedKey() = runTest {
        walletDao.insertWallet(firstWallet)
        val updatedWallet = firstWallet.copy(
            encryptedPrivateKeyBase64 = "updated-encrypted-private-key",
            label = "Updated wallet",
        )

        walletDao.insertWallet(updatedWallet)

        assertEquals(listOf(updatedWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun insertWalletsKeepsBothWallets() = runTest {
        walletDao.insertWallet(firstWallet)
        walletDao.insertWallet(secondWallet)

        assertEquals(
            setOf(firstWallet, secondWallet),
            walletDao.observeAllWallets().first().toSet(),
        )
    }

    @Test
    fun deleteWalletRemovesOnlyRequestedWallet() = runTest {
        walletDao.insertWallet(firstWallet)
        walletDao.insertWallet(secondWallet)

        walletDao.deleteWalletByAddress(firstWallet.address)

        assertEquals(listOf(secondWallet), walletDao.observeAllWallets().first())
    }

    @Test
    fun getEncryptedPrivateKeyReturnsStoredValue() = runTest {
        walletDao.insertWallet(firstWallet)

        assertEquals(
            firstWallet.encryptedPrivateKeyBase64,
            walletDao.getEncryptedPrivateKeyByAddress(firstWallet.address),
        )
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
        assertTrue(walletDao.observeAllWallets().first().isEmpty())
    }

    @Test
    fun updateWalletLabelPreservesTheEncryptedKey() = runTest {
        walletDao.insertWallet(firstWallet)

        walletDao.updateWalletLabel(firstWallet.address, "Renamed wallet")

        assertEquals(
            firstWallet.copy(label = "Renamed wallet"),
            walletDao.observeAllWallets().first().single(),
        )
    }
}
