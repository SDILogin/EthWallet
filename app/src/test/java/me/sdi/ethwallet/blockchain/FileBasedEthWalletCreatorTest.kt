package me.sdi.ethwallet.blockchain

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
import kotlin.io.path.ExperimentalPathApi

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

        // Verify address
        assertNotNull("Address should not be null", result.address)
        assertTrue(
            "Address should be a valid Ethereum address",
            result.address.toString().matches(Regex("^0x[0-9a-fA-F]{40}$")),
        )
        assertTrue(
            "Address should start with 0x",
            result.address.toString().startsWith("0x"),
        )

        // Verify private key
        assertNotNull("Private key should not be null", result.privateKey)

        // Verify seed phrase
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

        // Addresses should be different
        assertFalse(
            "Two generated wallets should have different addresses",
            wallet1.address.toString() == wallet2.address.toString(),
        )

        // Private keys should be different
        assertFalse(
            "Two generated wallets should have different private keys",
            wallet1.privateKey == wallet2.privateKey,
        )

        // Seed phrases should be different
        assertFalse(
            "Two generated wallets should have different seed phrases",
            wallet1.seedPhrase == wallet2.seedPhrase,
        )
    }

    @Test
    fun `createWallet should generate wallet with matching address and private key`() {
        val result = walletCreator.createWallet()

        // Calculate the address from the private key
        val ecKeyPair = ECKeyPair.create(result.privateKey)
        val calculatedAddress = "0x" + Keys.getAddress(ecKeyPair)

        // The calculated address should match the one in the result
        assertEquals(
            "Address should be derived from the private key",
            result.address.toString().lowercase(Locale.getDefault()),
            calculatedAddress.lowercase(Locale.getDefault()),
        )
    }

    @Test
    fun `createWallet should use tempFileProvider for wallet generation`() {
        var tempDirUsed = false

        // Create a custom fake provider that tracks if it was used
        val trackingTempProvider =
            object : TempFileProvider {
                override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
                    tempDirUsed = true
                    return fakeTempFileProvider.withTempDirectory(prefix, block)
                }
            }

        val trackedWalletCreator = FileBasedEthWalletCreator(trackingTempProvider)

        // When creating a wallet
        trackedWalletCreator.createWallet()

        // Then the temp directory provider should have been used
        assertTrue("Wallet creator should use TempFileProvider", tempDirUsed)
    }

    @Test
    fun `createWallet should generate valid mnemonic words`() {
        val result = walletCreator.createWallet()

        // All words in a BIP39 mnemonic should be valid English words
        // from the BIP39 wordlist (simplified check: just verify they're all lowercase alpha)
        result.seedPhrase.forEach { word ->
            assertTrue(
                "Seed phrase words should be valid lowercase alphabetic strings",
                word.all { it.isLetter() && it.isLowerCase() },
            )
        }

        // Words should be different from each other (at least some of them)
        val uniqueWords = result.seedPhrase.toSet()
        assertTrue(
            "Seed phrase should have some diversity in words",
            uniqueWords.size > 5,
        )
    }

    @Test
    fun `createWallet should properly clean up temporary files`() {
        // Create a tracking provider that checks if cleanup was called
        var dirCreated: File? = null
        var cleanupCalled = false

        val trackingProvider =
            object : TempFileProvider {
                override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
                    // Create a real temp directory
                    val tempDir = createTempDirectory(prefix).toFile()
                    dirCreated = tempDir

                    try {
                        return block(tempDir)
                    } finally {
                        // Mark that cleanup was called
                        cleanupCalled = true
                        // Clean up anyway for the test
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

        // When creating a wallet
        trackedWalletCreator.createWallet()

        // Then a directory should have been created
        assertNotNull("Temp directory should have been created", dirCreated)

        // And cleanup should have been called
        assertTrue("Cleanup should have been called", cleanupCalled)
    }
}
