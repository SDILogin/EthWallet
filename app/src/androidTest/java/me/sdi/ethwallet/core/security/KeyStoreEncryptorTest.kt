package me.sdi.ethwallet.core.security

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.kotlin.PrivateKey
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(AndroidJUnit4::class)
class KeyStoreEncryptorTest {
    @Before
    fun setUp() = deleteWalletAliases()

    @After
    fun tearDown() = deleteWalletAliases()

    @Test
    fun roundTripsPrivateKeyThroughTwoLayers() = runTest {
        val session = InMemoryPinSession().apply { set(randomKek()) }
        val encryptor = KeyStoreEncryptor(pinSession = session)

        val cipherText = encryptor.encryptPrivateKey(PRIVATE_KEY, ADDRESS)
        val decrypted = encryptor.decryptPrivateKey(cipherText, ADDRESS)

        val expectedBytes = PRIVATE_KEY.copyBytes()
        val decryptedBytes = decrypted.copyBytes()
        assertTrue("Round-trip must recover the original private key", decryptedBytes.contentEquals(expectedBytes))

        val inner = stripOuterKeystoreLayer(cipherText, ADDRESS)
        val privateKeyBytes = PRIVATE_KEY.copyBytes()
        assertFalse(
            "Stripping only the outer Keystore layer must not reveal the plaintext key",
            inner.contentEquals(privateKeyBytes),
        )
    }

    @Test
    fun clearedSessionBlocksEncrypt() {
        val encryptor = KeyStoreEncryptor(pinSession = InMemoryPinSession())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { encryptor.encryptPrivateKey(PRIVATE_KEY, ADDRESS) }
        }
    }

    @Test
    fun clearedSessionBlocksDecrypt() = runTest {
        val session = InMemoryPinSession().apply { set(randomKek()) }
        val encryptor = KeyStoreEncryptor(pinSession = session)
        val cipherText = encryptor.encryptPrivateKey(PRIVATE_KEY, ADDRESS)

        session.clear()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { encryptor.decryptPrivateKey(cipherText, ADDRESS) }
        }
    }

    @Test
    fun wrongKekFailsGcmAuthentication() = runTest {
        val cipherText = KeyStoreEncryptor(pinSession = InMemoryPinSession().apply { set(randomKek()) })
            .encryptPrivateKey(PRIVATE_KEY, ADDRESS)

        val wrongSessionEncryptor = KeyStoreEncryptor(pinSession = InMemoryPinSession().apply { set(randomKek()) })

        val error = assertThrows(GeneralSecurityException::class.java) {
            runBlocking { wrongSessionEncryptor.decryptPrivateKey(cipherText, ADDRESS) }
        }
        assertTrue(
            "Inner GCM tag mismatch must surface as an AEAD authentication failure, actual: $error",
            error is AEADBadTagException,
        )
    }

    private fun stripOuterKeystoreLayer(encryptedBase64: String, address: String): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val secretKey = keyStore.getKey("$ETH_ALIAS_PREFIX${address.lowercase()}", null) as SecretKey
        val data = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val body = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(body)
    }

    private fun randomKek(): ByteArray = ByteArray(MASTER_KEK_LENGTH).also { SecureRandom().nextBytes(it) }

    private fun deleteWalletAliases() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.aliases().toList()
            .filter { it.startsWith(ETH_ALIAS_PREFIX) }
            .forEach { keyStore.deleteEntry(it) }
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val MASTER_KEK_LENGTH = 32
        private const val ETH_ALIAS_PREFIX = "eth_wallet_key_"
        private const val ADDRESS = "0x1234567890abcdef1234567890abcdef12345678"
        private val PRIVATE_KEY = PrivateKey.fromBytes(
            "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray(),
        )
    }
}
