package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WalletRepositoryTest {
    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository

    private val createdAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
    private val createdPrivateKey = BigInteger("123456789012345678901234567890")
    private val createdSeedPhrase = List(12) { "created$it" }
    private val importedAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
    private val importedPrivateKey = BigInteger("987654321098765432109876543210")
    private val importedSeedPhrase = List(12) { "imported$it" }
    private val walletLabel = "Test wallet"
    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @BeforeTest
    fun setUp() {
        walletDao = FakeWalletDao()
        ethManager = FakeEthManager().also {
            it.creationResult = EthCreationResult(
                address = EthAddress(createdAddress),
                privateKey = createdPrivateKey,
                seedPhrase = createdSeedPhrase,
            )
            it.importResult = EthCreationResult(
                address = EthAddress(importedAddress),
                privateKey = importedPrivateKey,
                seedPhrase = importedSeedPhrase,
            )
        }
        privateKeyEncryptor = FakePrivateKeyEncryptor()
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = privateKeyEncryptor,
        )
    }

    @Test
    fun saveWalletPersistsEncryptedWalletAndMakesItActive() = runTest {
        walletRepository.saveWallet(ethManager.creationResult, walletLabel)
        assertEquals(
            listOf(
                WalletEntity(
                    address = createdAddress,
                    encryptedPrivateKeyBase64 = "encrypted_${createdPrivateKey.toString(16)}",
                    label = walletLabel,
                ),
            ),
            walletDao.wallets,
        )
        assertEquals(createdAddress, walletDao.currentWalletAddress)
    }

    @Test
    fun saveWalletPropagatesEncryptionFailureWithoutPersistingWallet() = runTest {
        privateKeyEncryptor.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.saveWallet(ethManager.creationResult, walletLabel) }

        assertEquals(emptyList(), walletDao.wallets)
        assertNull(walletDao.currentWalletAddress)
    }

    @Test
    fun saveWalletPropagatesDatabaseFailure() = runTest {
        walletDao.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.saveWallet(ethManager.creationResult, walletLabel) }
    }

    @Test
    fun importWalletPersistsEncryptedWalletAndMakesItActive() = runTest {
        walletRepository.importWallet(mnemonic, walletLabel)

        assertEquals(mnemonic, ethManager.lastImportedMnemonic)
        assertEquals(
            listOf(
                WalletEntity(
                    address = importedAddress,
                    encryptedPrivateKeyBase64 = "encrypted_${importedPrivateKey.toString(16)}",
                    label = walletLabel,
                ),
            ),
            walletDao.wallets,
        )
        assertEquals(importedAddress, walletDao.currentWalletAddress)
    }

    @Test
    fun importWalletPropagatesEthManagerFailureWithoutPersistingWallet() = runTest {
        ethManager.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.importWallet(mnemonic, walletLabel) }

        assertEquals(emptyList(), walletDao.wallets)
        assertNull(walletDao.currentWalletAddress)
    }

    @Test
    fun observeAllWalletsMapsDatabaseEntities() = runTest {
        walletDao.wallets = listOf(
            WalletEntity(createdAddress, "encrypted-created", "Created"),
            WalletEntity(importedAddress, "encrypted-imported", "Imported"),
        )

        assertEquals(
            listOf(
                EthWallet(label = "Created", address = EthAddress(createdAddress)),
                EthWallet(label = "Imported", address = EthAddress(importedAddress)),
            ),
            walletRepository.observeAllWallets().first(),
        )
    }

    @Test
    fun observeActiveWalletMapsCurrentWalletAndEmitsNullWithoutSelection() = runTest {
        val wallet = WalletEntity(createdAddress, "encrypted-created", walletLabel)
        walletDao.wallets = listOf(wallet)

        assertNull(walletRepository.observeActiveWallet().first())

        walletDao.currentWalletAddress = createdAddress

        assertEquals(
            EthWallet(label = walletLabel, address = EthAddress(createdAddress)),
            walletRepository.observeActiveWallet().first(),
        )
    }

    @Test
    fun setActiveWalletChangesCurrentWalletWithoutChangingWallets() = runTest {
        walletDao.wallets = listOf(
            WalletEntity(createdAddress, "encrypted-created", "Created"),
            WalletEntity(importedAddress, "encrypted-imported", "Imported"),
        )
        walletDao.currentWalletAddress = createdAddress

        walletRepository.setActiveWallet(EthAddress(importedAddress))

        assertEquals(importedAddress, walletDao.currentWalletAddress)
        assertEquals(2, walletDao.wallets.size)
    }

    @Test
    fun deleteWalletRemovesWalletAndClearsCurrentSelectionWhenNeeded() = runTest {
        walletDao.wallets = listOf(WalletEntity(createdAddress, "encrypted-created", walletLabel))
        walletDao.currentWalletAddress = createdAddress

        walletRepository.deleteWallet(EthAddress(createdAddress))

        assertEquals(emptyList(), walletDao.wallets)
        assertNull(walletDao.currentWalletAddress)
    }

    @Test
    fun getEncryptedPrivateKeyReturnsStoredValue() = runTest {
        walletDao.wallets = listOf(WalletEntity(createdAddress, "encrypted-created", walletLabel))

        assertEquals(
            "encrypted-created",
            walletRepository.getEncryptedPrivateKey(EthAddress(createdAddress)),
        )
    }
}
