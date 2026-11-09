package me.sdi.ethwallet.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.kotlin.PrivateKey
import me.sdi.ethwallet.util.preferencesDataStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MasterKekStorageAndroidKeyStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newStorage() =
        MasterKekStorageAndroidKeyStore(context = context, dispatcher = UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        wipe()
    }

    @Test
    fun createdMasterKekIsReadBack() = runTest {
        val masterKek = newStorage().create()

        assertEquals("The master KEK must be a 256-bit key", 32, masterKek.size)
        assertArrayEquals("The stored master KEK must be read back unchanged", masterKek, newStorage().read())
    }

    @Test
    fun everyCreateGeneratesARandomMasterKek() = runTest {
        val first = newStorage().create()
        val second = newStorage().create()

        assertFalse("Master KEK must be random per create, not PIN-derived", first.contentEquals(second))
    }

    @Test
    fun clearRemovesTheWrappedKekAndItsKeyStoreKey() = runTest {
        val storage = newStorage()
        storage.create()

        storage.clear()

        val error = runCatching { storage.read() }.exceptionOrNull()
        assertTrue("Reading a cleared master KEK is a programming error", error is IllegalStateException)
        assertFalse("The wrapping key must be deleted", loadKeyStore().containsAlias(KEK_WRAPPING_KEY_ALIAS))
    }

    @Test
    fun privateKeyEncryptedBeforePinChangeDecryptsAfterUnlockWithNewPin() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val session = InMemoryPinSession()
        val interactor = PinInteractor(
            pinCodeStorage = PinCodeStorageDataStore(context = context, dispatcher = dispatcher),
            masterKekStorage = MasterKekStorageAndroidKeyStore(context = context, dispatcher = dispatcher),
            pinAttemptsGuard = KeyStorePinAttemptsGuard(context = context),
            pinSession = session,
            userActivityManager = object : UserActivityManager {
                override fun onAnyUserAction() {}

                override fun onAppUnlocked() {}

                override fun onAppBackgrounded() {}

                override fun onAppForegrounded() {}
            },
        )
        interactor.setPin(PIN)
        val encryptor = KeyStoreEncryptor(pinSession = session)
        val encryptedPrivateKey = encryptor.encryptPrivateKey(PRIVATE_KEY, ADDRESS)

        interactor.changePin(NEW_PIN)
        session.clear()
        assertTrue("Unlocking with the new PIN must succeed", interactor.unlock(NEW_PIN))

        assertArrayEquals(
            "A private key stored before the PIN change must decrypt after unlocking with the new PIN",
            PRIVATE_KEY.copyBytes(),
            encryptor.decryptPrivateKey(encryptedPrivateKey, ADDRESS).copyBytes(),
        )
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wipe() = runBlocking {
        context.preferencesDataStore.edit { preferences ->
            preferences.remove(PIN_HASH_SALT)
            preferences.remove(PIN_HASH)
            preferences.remove(KEYSTORE_WRAPPED_MASTER_KEK)
            preferences.remove(PIN_ATTEMPTS_POINTER)
            preferences.remove(PIN_ATTEMPTS_BLOB)
        }
        val keyStore = loadKeyStore()
        keyStore.aliases().toList()
            .filter {
                it == KEK_WRAPPING_KEY_ALIAS ||
                    it.startsWith(PIN_ATTEMPTS_ALIAS_PREFIX) ||
                    it.startsWith(ETH_ALIAS_PREFIX)
            }
            .forEach { keyStore.deleteEntry(it) }
    }

    private companion object {
        private const val PIN = "123456"
        private const val NEW_PIN = "246810"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEK_WRAPPING_KEY_ALIAS = "wallet_pin_code_key"
        private const val PIN_ATTEMPTS_ALIAS_PREFIX = "pin_attempts_key_"
        private const val ETH_ALIAS_PREFIX = "eth_wallet_key_"
        private const val ADDRESS = "0x1234567890abcdef1234567890abcdef12345678"

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEYSTORE_WRAPPED_MASTER_KEK = stringPreferencesKey("keystore_wrapped_master_kek")
        private val PIN_ATTEMPTS_POINTER = intPreferencesKey("pin_attempts_pointer")
        private val PIN_ATTEMPTS_BLOB = stringPreferencesKey("pin_attempts_blob")
        private val PRIVATE_KEY = PrivateKey.fromBytes(
            "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray(),
        )
    }
}
