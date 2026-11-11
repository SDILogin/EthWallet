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
import me.sdi.ethwallet.core.security.PasswordStrength
import me.sdi.ethwallet.fakes.FakePasswordKekStorage
import me.sdi.ethwallet.fakes.FakePasswordStrengthChecker
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeUserActivityManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetPasswordViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val storage = FakePasswordKekStorage()
    private val strengthChecker = FakePasswordStrengthChecker()
    private val collectedNews = mutableListOf<SetPasswordNews>()
    private lateinit var viewModel: SetPasswordViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SetPasswordViewModel(
            passwordInteractor = PasswordInteractor(
                passwordKekStorage = storage,
                pinSession = FakePinSession(),
                userActivityManager = FakeUserActivityManager(),
            ),
            passwordStrengthChecker = strengthChecker,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun a_weak_password_can_still_be_set() = runTest(testDispatcher) {
        strengthChecker.strength = PasswordStrength.Weak
        collectNews()

        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(WEAK_PASSWORD))
        viewModel.onEvent(SetPasswordEvent.OnConfirmationChange(WEAK_PASSWORD))
        advanceUntilIdle()

        assertEquals(PasswordStrength.Weak, viewModel.viewState.value.strength, "The checker rating should be shown")
        assertTrue(viewModel.viewState.value.canContinue, "A weak password must not block the setup")

        viewModel.onEvent(SetPasswordEvent.OnContinueClick)
        advanceUntilIdle()

        assertEquals(WEAK_PASSWORD, storage.storedPassword, "The weak password should be stored")
        assertEquals(listOf<SetPasswordNews>(SetPasswordNews.PasswordSet), collectedNews, "Setup should finish")
    }

    @Test
    fun a_strong_rating_is_shown() = runTest(testDispatcher) {
        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(STRONG_PASSWORD))
        advanceUntilIdle()

        assertEquals(PasswordStrength.Strong, viewModel.viewState.value.strength, "The checker rating should be shown")
    }

    @Test
    fun continue_waits_until_the_password_is_rated() = runTest(testDispatcher) {
        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(STRONG_PASSWORD))
        viewModel.onEvent(SetPasswordEvent.OnConfirmationChange(STRONG_PASSWORD))

        assertFalse(viewModel.viewState.value.canContinue, "The strength must be shown before the password can be set")

        advanceUntilIdle()

        assertTrue(viewModel.viewState.value.canContinue, "A rated password can be set")
    }

    @Test
    fun a_scoring_failure_rates_the_password_weak() = runTest(testDispatcher) {
        strengthChecker.failure = IllegalStateException("Dictionaries unavailable")

        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(STRONG_PASSWORD))
        advanceUntilIdle()

        assertEquals(PasswordStrength.Weak, viewModel.viewState.value.strength, "An unrated password should warn")
    }

    @Test
    fun a_mismatching_confirmation_blocks_continue() = runTest(testDispatcher) {
        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(STRONG_PASSWORD))
        viewModel.onEvent(SetPasswordEvent.OnConfirmationChange("different"))
        advanceUntilIdle()

        assertTrue(viewModel.viewState.value.isMismatch, "A different confirmation should be reported")
        assertFalse(viewModel.viewState.value.canContinue, "A mismatch should block continue")
    }

    @Test
    fun an_empty_password_blocks_continue() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertFalse(viewModel.viewState.value.canContinue, "An empty password should block continue")
        assertNull(viewModel.viewState.value.strength, "An empty password has no strength rating")
    }

    @Test
    fun a_failed_setup_shows_an_error_and_allows_a_retry() = runTest(testDispatcher) {
        storage.createFailure = IllegalStateException("Secure Enclave unavailable")
        viewModel.onEvent(SetPasswordEvent.OnPasswordChange(STRONG_PASSWORD))
        viewModel.onEvent(SetPasswordEvent.OnConfirmationChange(STRONG_PASSWORD))
        advanceUntilIdle()

        viewModel.onEvent(SetPasswordEvent.OnContinueClick)
        advanceUntilIdle()

        assertEquals(SetPasswordError.SetupFailed, viewModel.viewState.value.error, "The failure should be shown")
        assertTrue(viewModel.viewState.value.canContinue, "The user should be able to try again")
    }

    private fun TestScope.collectNews() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collectedNews += it }
        }
    }

    private companion object {
        private const val WEAK_PASSWORD = "short"
        private const val STRONG_PASSWORD = "correct horse battery staple"
    }
}
