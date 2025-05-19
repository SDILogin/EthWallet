package me.sdi.ethwallet.core

import me.sdi.ethwallet.data.TempFileProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import java.io.File
import java.util.Locale

class FileBasedEthWalletCreatorTest {
    private lateinit var fakeTempFileProvider: FakeTempFileProvider
    private lateinit var walletCreator: FileBasedEthWalletCreator

    @Before
    fun setUp() {
        fakeTempFileProvider = FakeTempFileProvider()
        walletCreator = FileBasedEthWalletCreator(fakeTempFileProvider)
    }

    @After
    fun tearDown() {
        fakeTempFileProvider.cleanupAll()
    }

    @Test
    fun `createWallet should generate valid Ethereum wallet`() {
        val result = walletCreator.createWallet()

        assertNotNull("Address should not be null", result.address)
        assertTrue(
            "Address should be a valid Ethereum address",
            result.address.toString().matches(Regex("^0x[0-9a-fA-F]{40}$")),
        )
        assertTrue(
            "Address should start with 0x",
            result.address.toString().startsWith("0x"),
        )

        assertNotNull("Private key should not be null", result.privateKey)

        assertNotNull("Seed phrase should not be null", result.seedPhrase)
        assertEquals("Seed phrase should contain 12 words", 12, result.seedPhrase.size)
        result.seedPhrase.forEach { word ->
            assertTrue("Each word in seed phrase should not be empty", word.isNotEmpty())
        }
    }

    @Test
    fun `createWallet should generate different wallets on each call`() {
        val wallet1 = walletCreator.createWallet()
        val wallet2 = walletCreator.createWallet()

        assertFalse(
            "Two generated wallets should have different addresses",
            wallet1.address.toString() == wallet2.address.toString(),
        )

        assertFalse(
            "Two generated wallets should have different private keys",
            wallet1.privateKey == wallet2.privateKey,
        )

        assertFalse(
            "Two generated wallets should have different seed phrases",
            wallet1.seedPhrase == wallet2.seedPhrase,
        )
    }

    @Test
    fun `createWallet should generate wallet with matching address and private key`() {
        val result = walletCreator.createWallet()

        val ecKeyPair = ECKeyPair.create(result.privateKey)
        val calculatedAddress = "0x" + Keys.getAddress(ecKeyPair)

        assertEquals(
            "Address should be derived from the private key",
            result.address.toString().lowercase(Locale.getDefault()),
            calculatedAddress.lowercase(Locale.getDefault()),
        )
    }

    @Test
    fun `createWallet should use tempFileProvider for wallet generation`() {
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

        assertTrue("Wallet creator should use TempFileProvider", tempDirUsed)
    }

    @Test
    fun `createWallet should generate valid mnemonic words`() {
        val result = walletCreator.createWallet()

        result.seedPhrase.forEach { word ->
            assertTrue(
                "Seed phrase words should be valid lowercase alphabetic strings",
                word.all { it.isLetter() && it.isLowerCase() },
            )
        }

        val uniqueWords = result.seedPhrase.toSet()
        assertTrue(
            "Seed phrase should have some diversity in words",
            uniqueWords.size > 5,
        )
    }

    @Test
    fun `createWallet should properly clean up temporary files`() {
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

        assertNotNull("Temp directory should have been created", dirCreated)

        assertTrue("Cleanup should have been called", cleanupCalled)
    }
}
