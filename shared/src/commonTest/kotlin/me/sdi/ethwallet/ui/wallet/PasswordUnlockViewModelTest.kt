package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.security.PasswordInteractor
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakePasswordKekStorage
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordUnlockViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val storage = FakePasswordKekStorage().apply {
        storedPassword = PASSWORD
        storedKek = ByteArray(32)
    }
    private val pinSession = FakePinSession()
    private val dataCleaner = FakeDataCleaner()
    private val collectedNews = mutableListOf<PasswordUnlockNews>()
    private lateinit var viewModel: PasswordUnlockViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PasswordUnlockViewModel(
            passwordInteractor = PasswordInteractor(
                passwordKekStorage = storage,
                pinSession = pinSession,
                userActivityManager = FakeUserActivityManager(),
            ),
            dataCleaner = dataCleaner,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun the_right_password_unlocks() = runTest(testDispatcher) {
        collectNews()

        unlockWith(PASSWORD)

        assertEquals(listOf<PasswordUnlockNews>(PasswordUnlockNews.Unlocked), collectedNews, "The app should unlock")
        assertTrue(pinSession.isUnlocked.value, "The session should hold the master KEK")
    }

    @Test
    fun a_wrong_password_shows_an_error_and_stays_locked() = runTest(testDispatcher) {
        collectNews()

        unlockWith("wrong")

        assertEquals(PasswordUnlockError.WrongPassword, viewModel.viewState.value.error, "A wrong password error")
        assertFalse(viewModel.viewState.value.isBusy, "The user should be able to try again")
        assertTrue(collectedNews.isEmpty(), "A wrong password should not unlock")
    }

    @Test
    fun an_unlock_failure_shows_the_unlock_failed_error() = runTest(testDispatcher) {
        storage.readFailure = IllegalStateException("Secure Enclave key unusable")

        unlockWith(PASSWORD)

        assertEquals(PasswordUnlockError.UnlockFailed, viewModel.viewState.value.error, "A storage failure is not a wrong password")
    }

    @Test
    fun typing_clears_the_error() = runTest(testDispatcher) {
        unlockWith("wrong")

        viewModel.onEvent(PasswordUnlockEvent.OnPasswordChange("again"))

        assertEquals(null, viewModel.viewState.value.error, "Typing should clear the previous error")
    }

    @Test
    fun confirming_the_reset_wipes_the_data_and_logs_out() = runTest(testDispatcher) {
        collectNews()

        viewModel.onEvent(PasswordUnlockEvent.OnResetConfirm)
        advanceUntilIdle()

        assertEquals(1, dataCleaner.cleanCalledCount, "Reset should wipe the local data")
        assertEquals(listOf<PasswordUnlockNews>(PasswordUnlockNews.LoggedOut), collectedNews, "Reset should log out")
    }

    @Test
    fun a_failed_reset_shows_an_error_and_stays_on_the_lock_screen() = runTest(testDispatcher) {
        dataCleaner.shouldThrowException = true
        collectNews()

        viewModel.onEvent(PasswordUnlockEvent.OnResetConfirm)
        advanceUntilIdle()

        assertEquals(PasswordUnlockError.ResetFailed, viewModel.viewState.value.error, "A failed reset should be shown")
        assertTrue(collectedNews.isEmpty(), "A failed reset should not log out")
    }

    private fun TestScope.unlockWith(password: String) {
        viewModel.onEvent(PasswordUnlockEvent.OnPasswordChange(password))
        viewModel.onEvent(PasswordUnlockEvent.OnUnlockClick)
        advanceUntilIdle()
    }

    private fun TestScope.collectNews() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collectedNews += it }
        }
    }

    private companion object {
        private const val PASSWORD = "correct horse battery staple"
    }
}
