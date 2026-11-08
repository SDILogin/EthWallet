package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakeMasterKekStorage
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinInteractorTest {
    private val pinCodeStorage = FakePinCodeStorage()
    private val masterKekStorage = FakeMasterKekStorage()
    private val pinSession = FakePinSession()
    private val userActivityManager = FakeUserActivityManager()
    private val interactor = PinInteractorDefault(
        pinCodeStorage = pinCodeStorage,
        masterKekStorage = masterKekStorage,
        pinAttemptsGuard = FakePinAttemptsGuard(),
        pinSession = pinSession,
        userActivityManager = userActivityManager,
    )

    @Test
    fun setting_the_first_pin_creates_a_master_kek_and_unlocks_the_app() = runTest {
        interactor.setPin(PIN)

        assertTrue(interactor.hasPin(), "The PIN should be reported as set after onboarding")
        assertEquals(1, masterKekStorage.createCallCount, "Onboarding should create one master KEK")
        assertContentEquals(
            masterKekStorage.storedKek,
            pinSession.lastKek,
            "Onboarding should unlock the session with the stored master KEK",
        )
        assertEquals(1, userActivityManager.onAppUnlockedCallCount, "Onboarding should start the auto-lock timer")
    }

    @Test
    fun the_correct_pin_unlocks_the_session_with_the_stored_master_kek() = runTest {
        interactor.setPin(PIN)
        pinSession.clear()

        val unlocked = interactor.unlock(PIN)

        assertTrue(unlocked, "The stored PIN should unlock the app")
        assertContentEquals(
            masterKekStorage.storedKek,
            pinSession.lastKek,
            "Unlocking should publish the master KEK created at onboarding",
        )
        assertEquals(2, userActivityManager.onAppUnlockedCallCount, "Unlocking should restart the auto-lock timer")
    }

    @Test
    fun a_wrong_pin_leaves_the_session_locked() = runTest {
        interactor.setPin(PIN)
        pinSession.clear()

        val unlocked = interactor.unlock(WRONG_PIN)

        assertFalse(unlocked, "A wrong PIN must not unlock the app")
        assertNull(pinSession.lastKek, "A wrong PIN must not publish the master KEK")
        assertEquals(1, userActivityManager.onAppUnlockedCallCount, "A wrong PIN must not restart the auto-lock timer")
    }

    @Test
    fun unlocking_without_a_stored_master_kek_is_a_programming_error() = runTest {
        pinCodeStorage.storedPin = PIN

        assertFailsWith<IllegalStateException>("A PIN is never stored without a master KEK") {
            interactor.unlock(PIN)
        }
        assertNull(pinSession.lastKek, "A failed unlock must not publish a master KEK")
    }

    @Test
    fun changing_the_pin_keeps_the_master_kek_and_the_session() = runTest {
        interactor.setPin(PIN)
        val kekBeforeChange = assertNotNull(masterKekStorage.storedKek).copyOf()

        interactor.changePin(NEW_PIN)

        assertEquals(NEW_PIN, pinCodeStorage.storedPin, "The new PIN should replace the old one")
        assertEquals(1, masterKekStorage.createCallCount, "Changing the PIN must not create a new master KEK")
        assertContentEquals(kekBeforeChange, pinSession.lastKek, "Changing the PIN must keep the session unlocked")
        assertEquals(1, userActivityManager.onAppUnlockedCallCount, "Changing the PIN must not unlock the app again")
    }

    private companion object {
        private const val PIN = "123456"
        private const val NEW_PIN = "999999"
        private const val WRONG_PIN = "654321"
    }
}
