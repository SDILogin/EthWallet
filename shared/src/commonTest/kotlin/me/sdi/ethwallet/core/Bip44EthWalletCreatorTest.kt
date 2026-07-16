package me.sdi.ethwallet.core

import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bip44EthWalletCreatorTest {
    private val creator = Bip44EthWalletCreator()

    private val vectorMnemonic =
        "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
    private val vectorAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94"

    @Test
    fun importWallet_derives_the_same_account_as_trust_wallet_for_the_canonical_vector() {
        val result = creator.importWallet(vectorMnemonic)

        assertEquals(
            vectorAddress,
            result.address.toString().lowercase(Locale.getDefault()),
            "Mnemonic must derive the Trust Wallet/MetaMask account (BIP-44 m/44'/60'/0'/0/0)",
        )
    }

    @Test
    fun importWallet_normalizes_casing_and_whitespace() {
        val messy =
            "  ABANDON abandon\tabandon abandon abandon abandon " +
                "abandon abandon abandon  abandon abandon about  "

        val result = creator.importWallet(messy)

        assertEquals(
            vectorAddress,
            result.address.toString().lowercase(Locale.getDefault()),
            "Normalization should let a messy vector mnemonic derive the same account",
        )
    }

    @Test
    fun createWallet_then_importWallet_round_trips_to_the_same_account() {
        val created = creator.createWallet()

        val imported = creator.importWallet(created.seedPhrase.joinToString(" "))

        assertEquals(created.address, imported.address, "Imported address should match created address")
        assertEquals(created.privateKey, imported.privateKey, "Imported private key should match created key")
    }

    @Test
    fun importWallet_rejects_a_mnemonic_with_an_invalid_checksum() {
        val badChecksum =
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon"

        assertFailsWith<IllegalArgumentException>("Bad checksum mnemonic must be rejected") {
            creator.importWallet(badChecksum)
        }
    }

    @Test
    fun importWallet_rejects_a_mnemonic_with_an_unknown_word() {
        val badWord =
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon zzzz"

        assertFailsWith<IllegalArgumentException>("Mnemonic with an unknown word must be rejected") {
            creator.importWallet(badWord)
        }
    }

    @Test
    fun createWallet_produces_a_valid_twelve_word_wallet() {
        val result = creator.createWallet()

        assertTrue(
            result.address.toString().matches(Regex("^0x[0-9a-fA-F]{40}$")),
            "Address should be a valid 0x-prefixed 40-hex Ethereum address",
        )
        assertEquals(12, result.seedPhrase.size, "Seed phrase should contain 12 words")
        result.seedPhrase.forEach { word ->
            assertTrue(word.isNotEmpty(), "Each seed word should be non-empty")
        }
    }

    @Test
    fun createWallet_produces_distinct_wallets_on_each_call() {
        val first = creator.createWallet()
        val second = creator.createWallet()

        assertFalse(first.address == second.address, "Two created wallets should have different addresses")
        assertFalse(first.privateKey == second.privateKey, "Two created wallets should have different keys")
        assertFalse(first.seedPhrase == second.seedPhrase, "Two created wallets should have different phrases")
    }

    @Test
    fun createWallet_derives_the_address_from_the_private_key() {
        val result = creator.createWallet()

        val ecKeyPair = ECKeyPair.create(result.privateKey)
        val calculatedAddress = "0x" + Keys.getAddress(ecKeyPair)

        assertEquals(
            result.address.toString().lowercase(Locale.getDefault()),
            calculatedAddress.lowercase(Locale.getDefault()),
            "Address should be derived from the private key",
        )
    }
}
