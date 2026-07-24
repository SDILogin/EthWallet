package me.sdi.ethwallet.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.util.preferencesDataStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeStorageDataStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val userActivityManager = FakeUserActivityManager()

    private fun newStorage(pinSession: PinSession) =
        PinCodeStorageDataStore(
            context = context,
            userActivityManager = userActivityManager,
            pinAttemptsGuard = KeyStorePinAttemptsGuard(context = context),
            pinSession = pinSession,
            dispatcher = UnconfinedTestDispatcher(),
        )

    @After
    fun tearDown() {
        wipe()
    }

    @Test
    fun storeThenVerifyPopulatesSessionWithStoredKek() = runTest {
        val storeSession = InMemoryPinSession()
        val storage = newStorage(storeSession)
        storage.storePinCode(PIN)
        assertTrue("PIN code should be stored", storage.hasPinCode())
        val storedKek = storeSession.getOrThrow().copyOf()

        val verifySession = InMemoryPinSession()
        assertTrue(
            "Verification with the correct PIN must succeed",
            newStorage(verifySession).verifyPinCode(PIN),
        )
        assertArrayEquals(
            "Verify must recover the same master KEK that store produced",
            storedKek,
            verifySession.getOrThrow(),
        )
    }

    @Test
    fun verifyWrongPinLeavesSessionLocked() = runTest {
        newStorage(InMemoryPinSession()).storePinCode(PIN)

        val verifySession = InMemoryPinSession()
        assertFalse(
            "Verification with a wrong PIN must fail",
            newStorage(verifySession).verifyPinCode(WRONG_PIN),
        )
        assertThrows(IllegalStateException::class.java) { verifySession.getOrThrow() }
    }

    @Test
    fun clearRemovesArtifactsAndClearsSession() = runTest {
        val session = InMemoryPinSession()
        val storage = newStorage(session)
        storage.storePinCode(PIN)
        assertTrue("PIN code should be stored", storage.hasPinCode())

        storage.clearPinCode()

        assertFalse("PIN code should be gone after clearing", storage.hasPinCode())
        assertFalse("Verification should fail after clearing", storage.verifyPinCode(PIN))
        assertThrows(IllegalStateException::class.java) { session.getOrThrow() }
    }

    @Test
    fun verifyWithNoPinCodeSetFails() = runTest {
        assertFalse(newStorage(InMemoryPinSession()).verifyPinCode(PIN))
    }

    @Test
    fun saltPresentButWrappedMissingIsNotUsable() = runTest {
        val storage = newStorage(InMemoryPinSession())
        storage.storePinCode(PIN)

        context.preferencesDataStore.edit { it.remove(PIN_WRAPPED_KEK) }

        assertFalse("hasPinCode must be false with the wrapped KEK missing", storage.hasPinCode())
        assertFalse("Verification must fail with the wrapped KEK missing", storage.verifyPinCode(PIN))
    }

    @Test
    fun wrappedPresentButSaltMissingIsNotUsable() = runTest {
        val storage = newStorage(InMemoryPinSession())
        storage.storePinCode(PIN)

        context.preferencesDataStore.edit { it.remove(PIN_KDF_SALT) }

        assertFalse("hasPinCode must be false with the salt missing", storage.hasPinCode())
        assertFalse("Verification must fail with the salt missing", storage.verifyPinCode(PIN))
    }

    @Test
    fun twoStoresProduceDifferentMasterKeks() = runTest {
        val firstSession = InMemoryPinSession()
        newStorage(firstSession).storePinCode(PIN)
        val firstKek = firstSession.getOrThrow().copyOf()

        val secondSession = InMemoryPinSession()
        newStorage(secondSession).storePinCode(PIN)
        val secondKek = secondSession.getOrThrow().copyOf()

        assertFalse(
            "Master KEK must be random per store, not PIN-derived",
            firstKek.contentEquals(secondKek),
        )
    }

    @Test
    fun garbageWrappedKekVerifiesFalseWithoutCrashing() = runTest {
        val storage = newStorage(InMemoryPinSession())
        storage.storePinCode(PIN)

        context.preferencesDataStore.edit { it[PIN_WRAPPED_KEK] = "@@not-base-64@@" }

        assertFalse("Malformed wrapped KEK must verify false", storage.verifyPinCode(PIN))
    }

    @Test
    fun shortWrappedKekVerifiesFalseWithoutCrashing() = runTest {
        val storage = newStorage(InMemoryPinSession())
        storage.storePinCode(PIN)

        context.preferencesDataStore.edit {
            it[PIN_WRAPPED_KEK] = Base64.encodeToString(ByteArray(4), Base64.DEFAULT)
        }

        assertFalse("Blob shorter than the IV must verify false", storage.verifyPinCode(PIN))
    }

    private fun wipe() = runBlocking {
        context.preferencesDataStore.edit { preferences ->
            preferences.remove(PIN_KDF_SALT)
            preferences.remove(PIN_WRAPPED_KEK)
            preferences.remove(PIN_ATTEMPTS_POINTER)
            preferences.remove(PIN_ATTEMPTS_BLOB)
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.aliases().toList()
            .filter { it == PIN_KEY_ALIAS || it.startsWith(PIN_ATTEMPTS_ALIAS_PREFIX) }
            .forEach { keyStore.deleteEntry(it) }
    }

    private companion object {
        private const val PIN = "123456"
        private const val WRONG_PIN = "654321"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PIN_KEY_ALIAS = "wallet_pin_code_key"
        private const val PIN_ATTEMPTS_ALIAS_PREFIX = "pin_attempts_key_"

        private val PIN_KDF_SALT = stringPreferencesKey("pin_kdf_salt")
        private val PIN_WRAPPED_KEK = stringPreferencesKey("pin_wrapped_kek")
        private val PIN_ATTEMPTS_POINTER = intPreferencesKey("pin_attempts_pointer")
        private val PIN_ATTEMPTS_BLOB = stringPreferencesKey("pin_attempts_blob")
    }
}

private class FakeUserActivityManager : UserActivityManager {
    private val _showLockScreen = MutableStateFlow(false)
    override val showLockScreen: StateFlow<Boolean> = _showLockScreen.asStateFlow()

    override fun onAnyUserAction() {}

    override fun onAppUnlocked() {}
}
