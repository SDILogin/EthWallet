package me.sdi.ethwallet.core

import me.sdi.ethwallet.data.TempFileProvider
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileBasedEthWalletCreatorTest {
    private lateinit var fakeTempFileProvider: FakeTempFileProvider
    private lateinit var walletCreator: FileBasedEthWalletCreator

    @BeforeTest
    fun setUp() {
        fakeTempFileProvider = FakeTempFileProvider()
        walletCreator = FileBasedEthWalletCreator(fakeTempFileProvider)
    }

    @AfterTest
    fun tearDown() {
        fakeTempFileProvider.cleanupAll()
    }

    @Test
    fun createWallet_should_generate_valid_Ethereum_wallet() {
        val result = walletCreator.createWallet()

        assertNotNull(result.address, "Address should not be null")
        assertTrue(
            result.address.toString().matches(Regex("^0x[0-9a-fA-F]{40}$")),
            "Address should be a valid Ethereum address",
        )
        assertTrue(
            result.address.toString().startsWith("0x"),
            "Address should start with 0x",
        )

        assertNotNull(result.privateKey, "Private key should not be null")

        assertNotNull(result.seedPhrase, "Seed phrase should not be null")
        assertEquals(12, result.seedPhrase.size, "Seed phrase should contain 12 words")
        result.seedPhrase.forEach { word ->
            assertTrue(word.isNotEmpty(), "Each word in seed phrase should not be empty")
        }
    }

    @Test
    fun createWallet_should_generate_different_wallets_on_each_call() {
        val wallet1 = walletCreator.createWallet()
        val wallet2 = walletCreator.createWallet()

        assertFalse(
            wallet1.address.toString() == wallet2.address.toString(),
            "Two generated wallets should have different addresses",
        )

        assertFalse(
            wallet1.privateKey == wallet2.privateKey,
            "Two generated wallets should have different private keys",
        )

        assertFalse(
            wallet1.seedPhrase == wallet2.seedPhrase,
            "Two generated wallets should have different seed phrases",
        )
    }

    @Test
    fun createWallet_should_generate_wallet_with_matching_address_and_private_key() {
        val result = walletCreator.createWallet()

        val ecKeyPair = ECKeyPair.create(result.privateKey)
        val calculatedAddress = "0x" + Keys.getAddress(ecKeyPair)

        assertEquals(
            result.address.toString().lowercase(Locale.getDefault()),
            calculatedAddress.lowercase(Locale.getDefault()),
            "Address should be derived from the private key",
        )
    }

    @Test
    fun createWallet_should_use_tempFileProvider_for_wallet_generation() {
        var tempDirUsed = false

        val trackingTempProvider =
            object : TempFileProvider {
                override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
                    tempDirUsed = true
                    return fakeTempFileProvider.withTempDirectory(prefix, block)
                }
            }

        val trackedWalletCreator = FileBasedEthWalletCreator(trackingTempProvider)

        trackedWalletCreator.createWallet()

        assertTrue(tempDirUsed, "Wallet creator should use TempFileProvider")
    }

    @Test
    fun createWallet_should_generate_valid_mnemonic_words() {
        val result = walletCreator.createWallet()

        result.seedPhrase.forEach { word ->
            assertTrue(
                word.all { it.isLetter() && it.isLowerCase() },
                "Seed phrase words should be valid lowercase alphabetic strings",
            )
        }

        val uniqueWords = result.seedPhrase.toSet()
        assertTrue(
            uniqueWords.size > 5,
            "Seed phrase should have some diversity in words",
        )
    }

    @Test
    fun createWallet_should_properly_clean_up_temporary_files() {
        var dirCreated: File? = null
        var cleanupCalled = false

        val trackingProvider =
            object : TempFileProvider {
                override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
                    val tempDir = createTempDirectory(prefix).toFile()
                    dirCreated = tempDir

                    try {
                        return block(tempDir)
                    } finally {
                        cleanupCalled = true
                        if (tempDir.exists()) {
                            tempDir.deleteRecursively()
                        }
                    }
                }

                @OptIn(kotlin.io.path.ExperimentalPathApi::class)
                private fun createTempDirectory(prefix: String): java.nio.file.Path {
                    return kotlin.io.path.createTempDirectory(prefix)
                }
            }

        val trackedWalletCreator = FileBasedEthWalletCreator(trackingProvider)

        trackedWalletCreator.createWallet()

        assertNotNull(dirCreated, "Temp directory should have been created")

        assertTrue(cleanupCalled, "Cleanup should have been called")
    }
}
