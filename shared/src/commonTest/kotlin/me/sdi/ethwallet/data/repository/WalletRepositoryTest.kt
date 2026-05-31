package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthWalletCreator
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
    private lateinit var walletCreator: FakeEthWalletCreator
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

    @BeforeTest
    fun setUp() {
        walletDao = FakeWalletDao()
        walletCreator = FakeEthWalletCreator()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        walletCreator.creationResult = EthCreationResult(
            address = EthAddress(testAddress),
            privateKey = testPrivateKey,
            seedPhrase = testSeedPhrase,
        )

        walletRepository = WalletRepository(
            walletDao = walletDao,
            walletCreator = walletCreator,
            privateKeyEncryptor = privateKeyEncryptor,
        )
    }

    @Test
    fun createWallet_should_generate_wallet_encrypt_private_key_save_to_database_and_return_seed_phrase() = runTest {
        val result = walletRepository.createWallet(testLabel)

        assertEquals(testSeedPhrase, result, "Seed phrase should match the one from wallet creator")
        assertEquals(1, walletDao.wallets.size)
        val capturedEntity = walletDao.wallets.first()
        assertEquals(testAddress, capturedEntity.address, "Address should match")
        assertEquals(testEncryptedKey, capturedEntity.encryptedPrivateKeyBase64, "Encrypted key should match")
        assertEquals(testLabel, capturedEntity.label, "Label should match")
        assertEquals(true, capturedEntity.isActive, "isActive should be true by default")
    }

    @Test
    fun createWallet_should_use_default_label_when_not_provided() = runTest {
        walletRepository.createWallet()

        val capturedEntity = walletDao.wallets.first()
        assertEquals("My Wallet", capturedEntity.label, "Label should be the default")
        assertEquals(true, capturedEntity.isActive, "isActive should be true by default")
    }

    @Test
    fun createWallet_should_set_isActive_to_false_when_specified() = runTest {
        walletRepository.createWallet(setAsActive = false)

        val capturedEntity = walletDao.wallets.first()
        assertEquals(false, capturedEntity.isActive, "isActive should be false")
    }

    @Test
    fun createWallet_should_propagate_exceptions_from_wallet_creator() = runTest {
        walletCreator.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.createWallet()
        }
    }

    @Test
    fun createWallet_should_propagate_exceptions_from_encryptor() = runTest {
        privateKeyEncryptor.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.createWallet()
        }
    }

    @Test
    fun createWallet_should_propagate_exceptions_from_database() = runTest {
        walletDao.shouldThrowException = true

        assertFailsWith<Exception> {
            walletRepository.createWallet()
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
