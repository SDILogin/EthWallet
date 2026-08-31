package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.kotlin.PrivateKey
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                privateKey = privateKey(createdPrivateKey),
                seedPhrase = createdSeedPhrase,
            )
            it.importResult = EthCreationResult(
                address = EthAddress(importedAddress),
                privateKey = privateKey(importedPrivateKey),
                seedPhrase = importedSeedPhrase,
            )
        }
        privateKeyEncryptor = FakePrivateKeyEncryptor()
        walletRepository = WalletRepository(walletDao, ethManager, privateKeyEncryptor)
    }

    @Test
    fun saveWalletPersistsEncryptedWallet() = runTest {
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
    }

    @Test
    fun saveWalletPropagatesEncryptionFailureWithoutPersistingWallet() = runTest {
        privateKeyEncryptor.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.saveWallet(ethManager.creationResult, walletLabel) }

        assertEquals(emptyList(), walletDao.wallets)
    }

    @Test
    fun saveWalletPropagatesDatabaseFailure() = runTest {
        walletDao.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.saveWallet(ethManager.creationResult, walletLabel) }
    }

    @Test
    fun importWalletPersistsEncryptedWallet() = runTest {
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
    }

    @Test
    fun importWalletPropagatesEthManagerFailureWithoutPersistingWallet() = runTest {
        ethManager.shouldThrowException = true

        assertFailsWith<RuntimeException> { walletRepository.importWallet(mnemonic, walletLabel) }

        assertEquals(emptyList(), walletDao.wallets)
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
    fun deleteWalletRemovesRequestedWallet() = runTest {
        walletDao.wallets = listOf(WalletEntity(createdAddress, "encrypted-created", walletLabel))

        walletRepository.deleteWallet(EthAddress(createdAddress))

        assertEquals(emptyList(), walletDao.wallets)
    }

    @Test
    fun renameWalletUpdatesOnlyTheLabel() = runTest {
        val originalWallet = WalletEntity(createdAddress, "encrypted-created", walletLabel)
        walletDao.wallets = listOf(originalWallet)

        walletRepository.renameWallet(EthAddress(createdAddress), "Renamed wallet")

        assertEquals(
            listOf(originalWallet.copy(label = "Renamed wallet")),
            walletDao.wallets,
        )
    }

    @Test
    fun getEncryptedPrivateKeyReturnsStoredValue() = runTest {
        walletDao.wallets = listOf(WalletEntity(createdAddress, "encrypted-created", walletLabel))

        assertEquals(
            "encrypted-created",
            walletRepository.getEncryptedPrivateKey(EthAddress(createdAddress)),
        )
    }

    private fun privateKey(value: BigInteger): PrivateKey {
        val source = value.toByteArray()
        val bytes = ByteArray(32)
        val sourceStart = if (source.size > bytes.size) source.size - bytes.size else 0
        source.copyInto(bytes, bytes.size - (source.size - sourceStart), sourceStart)
        source.fill(0)
        return try {
            PrivateKey.fromBytes(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
