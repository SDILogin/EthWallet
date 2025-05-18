package me.sdi.ethwallet.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class WalletRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mock dependencies
    private lateinit var walletDao: WalletDao
    private lateinit var walletCreator: EthWalletCreator
    private lateinit var keyStoreEncryptor: KeyStoreEncryptor

    // Class under test
    private lateinit var walletRepository: WalletRepository

    // Test data
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
    private val testEncryptedKey = "encrypted_base64_string"
    private val testLabel = "Test Wallet"

    @Before
    fun setUp() {
        // Setup mocks
        walletDao = mockk(relaxed = true)
        walletCreator = mockk()
        keyStoreEncryptor = mockk()

        // Configure default behavior for mocks
        every { walletCreator.createWallet() } returns
            EthCreationResult(
                address = EthAddress(testAddress),
                privateKey = testPrivateKey,
                seedPhrase = testSeedPhrase,
            )

        every {
            keyStoreEncryptor.encryptPrivateKey(
                privateKey = testPrivateKey,
                address = testAddress,
            )
        } returns testEncryptedKey

        // Create instance of class under test
        walletRepository =
            WalletRepository(
                walletDao = walletDao,
                walletCreator = walletCreator,
                keyStoreEncryptor = keyStoreEncryptor,
            )
    }

    @Test
    fun `createWallet should generate wallet, encrypt private key, save to database and return seed phrase`() =
        runTest {
            val walletEntitySlot = slot<WalletEntity>()
            coEvery { walletDao.insertWallet(capture(walletEntitySlot)) } returns Unit

            val result = walletRepository.createWallet(testLabel)

            // Assert
            assertEquals("Seed phrase should match the one from wallet creator", testSeedPhrase, result)
            coVerify(exactly = 1) { walletCreator.createWallet() }
            coVerify(exactly = 1) {
                keyStoreEncryptor.encryptPrivateKey(
                    privateKey = testPrivateKey,
                    address = testAddress,
                )
            }
            coVerify(exactly = 1) { walletDao.insertWallet(any()) }
            val capturedEntity = walletEntitySlot.captured
            assertEquals("Address should match", testAddress, capturedEntity.address)
            assertEquals("Encrypted key should match", testEncryptedKey, capturedEntity.encryptedPrivateKeyBase64)
            assertEquals("Label should match", testLabel, capturedEntity.label)
        }

    @Test
    fun `createWallet should use default label when not provided`() =
        runTest {
            val walletEntitySlot = slot<WalletEntity>()
            coEvery { walletDao.insertWallet(capture(walletEntitySlot)) } returns Unit

            walletRepository.createWallet()

            val capturedEntity = walletEntitySlot.captured
            assertEquals("Label should be the default", "My Wallet", capturedEntity.label)
        }

    @Test(expected = Exception::class)
    fun `createWallet should propagate exceptions from wallet creator`() =
        runTest {
            every { walletCreator.createWallet() } throws RuntimeException("Creation failed")

            walletRepository.createWallet()
        }

    @Test(expected = Exception::class)
    fun `createWallet should propagate exceptions from encryptor`() =
        runTest {
            every {
                keyStoreEncryptor.encryptPrivateKey(
                    privateKey = any(),
                    address = any(),
                )
            } throws SecurityException("Encryption failed")

            walletRepository.createWallet()
        }

    @Test(expected = Exception::class)
    fun `createWallet should propagate exceptions from database`() =
        runTest {
            coEvery { walletDao.insertWallet(any()) } throws RuntimeException("Database error")

            walletRepository.createWallet()
        }
}
