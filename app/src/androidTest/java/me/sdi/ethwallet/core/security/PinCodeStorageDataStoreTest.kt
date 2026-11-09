package me.sdi.ethwallet.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.util.preferencesDataStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeStorageDataStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newStorage() =
        PinCodeStorageDataStore(context = context, dispatcher = UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        runBlocking {
            context.preferencesDataStore.edit { preferences ->
                preferences.remove(PIN_HASH_SALT)
                preferences.remove(PIN_HASH)
            }
        }
    }

    @Test
    fun storedPinVerifies() = runTest {
        val storage = newStorage()
        storage.storePinCode(PIN)

        assertTrue("PIN code should be stored", storage.hasPinCode())
        assertTrue("Verification with the correct PIN must succeed", newStorage().verifyPinCode(PIN))
    }

    @Test
    fun wrongPinIsRejected() = runTest {
        newStorage().storePinCode(PIN)

        assertFalse("Verification with a wrong PIN must fail", newStorage().verifyPinCode(WRONG_PIN))
    }

    @Test
    fun clearRemovesThePinHash() = runTest {
        val storage = newStorage()
        storage.storePinCode(PIN)

        storage.clearPinCode()

        assertFalse("PIN code should be gone after clearing", storage.hasPinCode())
    }

    @Test
    fun verifyWithNoPinCodeSetThrows() = runTest {
        val storage = newStorage()

        val error = runCatching { storage.verifyPinCode(PIN) }.exceptionOrNull()

        assertTrue("Verifying without a stored PIN is a programming error", error is IllegalStateException)
    }

    @Test
    fun missingPinHashIsNotAStoredPin() = runTest {
        val storage = newStorage()
        storage.storePinCode(PIN)

        context.preferencesDataStore.edit { it.remove(PIN_HASH) }

        assertFalse("hasPinCode must be false with the PIN hash missing", storage.hasPinCode())
    }

    @Test
    fun changedPinVerifiesAndOldPinDoesNot() = runTest {
        newStorage().storePinCode(PIN)

        newStorage().changePinCode(NEW_PIN)

        assertTrue("The new PIN must verify", newStorage().verifyPinCode(NEW_PIN))
        assertFalse("The old PIN must no longer verify", newStorage().verifyPinCode(PIN))
    }

    @Test
    fun everyChangeStoresAFreshSaltAndPinHash() = runTest {
        val storage = newStorage()
        storage.storePinCode(PIN)
        val before = context.preferencesDataStore.data.first()

        storage.changePinCode(PIN)

        val after = context.preferencesDataStore.data.first()
        assertNotEquals("Each PIN change must use a new salt", before[PIN_HASH_SALT], after[PIN_HASH_SALT])
        assertNotEquals("Each PIN change must store a new PIN hash", before[PIN_HASH], after[PIN_HASH])
        assertTrue("Re-setting the same PIN must keep it valid", storage.verifyPinCode(PIN))
    }

    private companion object {
        private const val PIN = "123456"
        private const val NEW_PIN = "246810"
        private const val WRONG_PIN = "654321"

        private val PIN_HASH_SALT = stringPreferencesKey("pin_hash_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
    }
}
