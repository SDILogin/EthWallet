package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletRepositoryTest {
    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository

    private val testAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
    private val testPrivateKey = BigInteger("123456789012345678901234567890123456789012345678901234567890")
    private val testSeedPhrase =
        listOf(
            "abandon",
            "ability",
            "able",
            "about",
            "above",
            "absent",
            "absorb",
            "abstract",
            "absurd",
            "abuse",
            "access",
            "accident",
        )
    private val testEncryptedKey = "encrypted_13aaf504e4bc1e62173f87a4378c37b49c8ccff196ce3f0ad2"
    private val testLabel = "Test Wallet"

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
    private val testImportAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
    private val testImportPrivateKey = BigInteger("987654321098765432109876543210987654321098765432109876543210")
    private val testImportEncryptedKey = "encrypted_" + testImportPrivateKey.toString(16)

    @BeforeTest
    fun setUp() {
        walletDao = FakeWalletDao()
        ethManager = FakeEthManager()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        ethManager.creationResult = EthCreationResult(
            address = EthAddress(testAddress),
            privateKey = testPrivateKey,
            seedPhrase = testSeedPhrase,
        )
        ethManager.importResult = EthCreationResult(
            address = EthAddress(testImportAddress),
            privateKey = testImportPrivateKey,
            seedPhrase = testSeedPhrase,
        )

        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = privateKeyEncryptor,
        )
    }

    @Test
    fun createWallet_should_generate_wallet_and_return_seed_phrase_without_persisting() = runTest {
        val result = walletRepository.createWallet(testLabel)

        assertEquals(testSeedPhrase, result, "Seed phrase should match the one from EthManager")
        assertTrue(walletDao.wallets.isEmpty(), "createWallet should not insert into the database")
    }

    @Test
    fun createWallet_should_propagate_exceptions_from_EthManager() = runTest {
        ethManager.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.createWallet("My wallet")
        }
    }

    @Test
    fun importWallet_should_derive_wallet_without_persisting() = runTest {
        walletRepository.importWallet(testMnemonic, testLabel)

        assertEquals(testMnemonic, ethManager.lastImportedMnemonic, "Mnemonic should be forwarded to EthManager")
        assertTrue(walletDao.wallets.isEmpty(), "importWallet should not insert into the database")
    }

    @Test
    fun importWallet_should_propagate_exceptions_from_EthManager() = runTest {
        ethManager.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.importWallet(testMnemonic, testLabel)
        }
    }

    @Test
    fun persistPendingWallet_should_throw_when_nothing_is_pending() = runTest {
        assertFailsWith<IllegalStateException> {
            walletRepository.persistPendingWallet()
        }
    }

    @Test
    fun persistPendingWallet_should_encrypt_and_insert_the_pending_created_wallet_as_active() = runTest {
        walletRepository.createWallet(testLabel)

        walletRepository.persistPendingWallet()

        assertEquals(1, walletDao.wallets.size, "Pending wallet should be inserted")
        val capturedEntity = walletDao.wallets.first()
        assertEquals(testAddress, capturedEntity.address, "Address should match")
        assertEquals(testEncryptedKey, capturedEntity.encryptedPrivateKeyBase64, "Encrypted key should match")
        assertEquals(testLabel, capturedEntity.label, "Label should match")
        assertEquals(true, capturedEntity.isActive, "Persisted wallet should be active")
    }

    @Test
    fun persistPendingWallet_should_encrypt_and_insert_the_pending_imported_wallet_as_active() = runTest {
        walletRepository.importWallet(testMnemonic, testLabel)

        walletRepository.persistPendingWallet()

        assertEquals(1, walletDao.wallets.size, "Pending wallet should be inserted")
        val capturedEntity = walletDao.wallets.first()
        assertEquals(testImportAddress, capturedEntity.address, "Address should match the imported wallet")
        assertEquals(testImportEncryptedKey, capturedEntity.encryptedPrivateKeyBase64, "Encrypted key should match")
        assertEquals(testLabel, capturedEntity.label, "Label should match")
        assertEquals(true, capturedEntity.isActive, "Imported wallet should be active")
    }

    @Test
    fun persistPendingWallet_should_clear_previously_active_wallets() = runTest {
        val previousAddress = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"
        walletDao.wallets = listOf(
            WalletEntity(
                address = previousAddress,
                encryptedPrivateKeyBase64 = "",
                label = "Old Wallet",
                isActive = true,
            ),
        )
        walletRepository.importWallet(testMnemonic, testLabel)

        walletRepository.persistPendingWallet()

        assertEquals(
            false,
            walletDao.wallets.find { it.address == previousAddress }?.isActive,
            "Previously active wallet should be deactivated",
        )
        assertEquals(
            true,
            walletDao.wallets.find { it.address == testImportAddress }?.isActive,
            "Imported wallet should be active",
        )
    }

    @Test
    fun persistPendingWallet_should_clear_the_pending_state_after_persisting() = runTest {
        walletRepository.createWallet(testLabel)
        walletRepository.persistPendingWallet()

        assertFailsWith<IllegalStateException>("Nothing should be pending after a successful persist") {
            walletRepository.persistPendingWallet()
        }
    }

    @Test
    fun a_second_createWallet_should_replace_the_pending_wallet() = runTest {
        walletRepository.createWallet(testLabel)
        ethManager.creationResult = EthCreationResult(
            address = EthAddress(testImportAddress),
            privateKey = testImportPrivateKey,
            seedPhrase = testSeedPhrase,
        )

        walletRepository.createWallet("Second Wallet")
        walletRepository.persistPendingWallet()

        assertEquals(1, walletDao.wallets.size, "Only the latest pending wallet should be persisted")
        val capturedEntity = walletDao.wallets.first()
        assertEquals(
            testImportAddress,
            capturedEntity.address,
            "Second createWallet should replace the first pending wallet",
        )
        assertEquals("Second Wallet", capturedEntity.label, "Label should match the second createWallet call")
    }

    @Test
    fun persistPendingWallet_should_propagate_exceptions_from_encryptor() = runTest {
        walletRepository.createWallet(testLabel)
        privateKeyEncryptor.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.persistPendingWallet()
        }
    }

    @Test
    fun persistPendingWallet_should_propagate_exceptions_from_database() = runTest {
        walletRepository.createWallet(testLabel)
        walletDao.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.persistPendingWallet()
        }
    }

    @Test
    fun getAllWallets_should_return_all_wallets_from_the_database() = runTest {
        val testWallets =
            listOf(
                WalletEntity(
                    address = testAddress,
                    encryptedPrivateKeyBase64 = testEncryptedKey,
                    label = testLabel,
                    isActive = true,
                ),
                WalletEntity(
                    address = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF",
                    encryptedPrivateKeyBase64 = "another_encrypted_key",
                    label = "Another Wallet",
                    isActive = false,
                ),
            )

        walletDao.wallets = testWallets

        val result = walletRepository.getAllWallets()

        val expectedEthWallets = listOf(
            EthWallet(
                label = testLabel,
                address = EthAddress(testAddress),
            ),
            EthWallet(
                label = "Another Wallet",
                address = EthAddress("0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"),
            ),
        )
        assertEquals(expectedEthWallets, result, "Should return all wallets")
    }

    @Test
    fun setActiveWallet_should_clear_active_wallets_and_set_the_specified_wallet_as_active() = runTest {
        walletDao.wallets = listOf(
            WalletEntity(address = "0x123", encryptedPrivateKeyBase64 = "", label = "", isActive = true),
            WalletEntity(
                address = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF",
                encryptedPrivateKeyBase64 = "",
                label = "",
                isActive = false,
            ),
        )
        val address = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"

        walletRepository.setActiveWallet(EthAddress(address))

        val wallets = walletDao.getAllWallets()
        assertEquals(false, wallets.find { it.address == "0x123" }?.isActive)
        assertEquals(true, wallets.find { it.address == address }?.isActive)
    }

    @Test
    fun deleteWallet_should_delete_wallet_by_address() = runTest {
        val address = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"
        walletDao.wallets = listOf(
            WalletEntity(address = address, encryptedPrivateKeyBase64 = "", label = ""),
        )

        walletRepository.deleteWallet(EthAddress(address))

        assertTrue(walletDao.wallets.isEmpty())
    }
}
