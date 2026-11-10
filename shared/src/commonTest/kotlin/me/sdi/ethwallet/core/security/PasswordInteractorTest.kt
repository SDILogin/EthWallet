package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakePasswordKekStorage
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordInteractorTest {
    private val storage = FakePasswordKekStorage()
    private val pinSession = FakePinSession()
    private val interactor = PasswordInteractor(
        passwordKekStorage = storage,
        pinSession = pinSession,
        userActivityManager = FakeUserActivityManager(),
    )

    @Test
    fun setting_a_password_creates_the_master_kek_and_unlocks() = runTest {
        interactor.setPassword(PASSWORD)

        assertContentEquals(storage.storedKek, pinSession.getOrThrow(), "The new master KEK should unlock the session")
        assertTrue(interactor.hasPassword(), "A set password should be reported")
    }

    @Test
    fun the_right_password_unlocks_the_session() = runTest {
        interactor.setPassword(PASSWORD)
        pinSession.clear()

        assertTrue(interactor.unlock(PASSWORD), "The right password should unlock")
        assertTrue(pinSession.isUnlocked.value, "The session should hold the master KEK")
    }

    @Test
    fun a_wrong_password_keeps_the_session_locked() = runTest {
        interactor.setPassword(PASSWORD)
        pinSession.clear()

        assertFalse(interactor.unlock("wrong"), "A wrong password should not unlock")
        assertFalse(pinSession.isUnlocked.value, "The session should stay locked")
    }

    private companion object {
        private const val PASSWORD = "correct horse battery staple"
    }
}
