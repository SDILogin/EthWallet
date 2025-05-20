package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.security.FakePinCodeStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPinCodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: VerifyPinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private val testPin = "123456"
    private val wrongPin = "000000"

    private val collectedNews = mutableListOf<VerifyPinCodeNews>()

    @Before
    fun setUp() {
        pinCodeStorage = FakePinCodeStorage()
        runBlocking {
            pinCodeStorage.storePinCode(testPin)
        }
        viewModel = VerifyPinCodeViewModel(pinCodeStorage)
        Logger.enabled = true
    }

    @Test
    fun `initial state should have default values`() = runTest {
        val initialState = viewModel.viewState.first()

        assertEquals("Initial PIN should be empty", "", initialState.enteredPin)
        assertNull("No error message should be present initially", initialState.errorMessage)
        assertEquals("Initial attempts should be set to MAX_ATTEMPTS (3)", 3, initialState.remainingAttempts)
        assertFalse("Verification should not be in progress initially", initialState.isVerifying)
        assertFalse("Lock state should be inactive initially", initialState.isLocked)
        assertEquals("Lock countdown should be zero initially", 0, initialState.lockCountdown)
    }

    @Test
    fun `should verify PIN successfully and emit PinVerified event`() = runTest {
        collectNews()

        inputPin(testPin)

        assertEquals(
            "Success should emit PinVerified news",
            VerifyPinCodeNews.PinVerified,
            collectedNews.firstOrNull(),
        )

        val finalState = viewModel.viewState.first()
        assertTrue("Verification flag should be set during PIN check", finalState.isVerifying)
        assertEquals("Entered PIN should match test PIN", testPin, finalState.enteredPin)
        assertNull("No error message should be present after successful PIN", finalState.errorMessage)
    }

    @Test
    fun `digit clicks should append to PIN`() = runTest {
        inputPin("123")

        val state = viewModel.viewState.first()
        assertEquals("Digits should be appended to PIN", "123", state.enteredPin)
        assertFalse("Verification shouldn't start until PIN is complete", state.isVerifying)
    }

    @Test
    fun `delete click should remove last digit from PIN`() = runTest {
        inputPin("123")
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("Delete should remove the last entered digit", "12", state.enteredPin)
    }

    @Test
    fun `delete click should have no effect when PIN is empty`() = runTest {
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("Delete should have no effect on empty PIN", "", state.enteredPin)
    }

    @Test
    fun `invalid PIN verification should decrement remaining attempts`() = runTest {
        inputPin(wrongPin)

        val state = viewModel.viewState.first()
        assertEquals("Pin must be reset after wrong attempt", "", state.enteredPin)
        assertFalse("Error message after invalid pin input", state.errorMessage.isNullOrBlank())
        assertEquals("remainingAttempts must change after wrong pin input", 2, state.remainingAttempts)
        assertFalse("Flag isVerifying must be reset after pin check to unlock numpad", state.isVerifying)
        assertFalse("User must be able to input pin again when there are 2 more attempts left", state.isLocked)
    }

    @Test
    fun `handleInvalidPin should decrement remaining attempts until locked`() = runTest {
        val initialState = viewModel.viewState.first()
        assertEquals("Initial attempts should be MAX_ATTEMPTS (3)", 3, initialState.remainingAttempts)

        inputPin(wrongPin)
        val state1 = viewModel.viewState.first()
        assertEquals("First wrong attempt should reduce attempts to 2", 2, state1.remainingAttempts)

        inputPin(wrongPin)
        val state2 = viewModel.viewState.first()
        assertEquals("Second wrong attempt should reduce attempts to 1", 1, state2.remainingAttempts)
    }

    @Test
    fun `error message should be cleared when entering digits after error`() = runTest {
        inputPin(wrongPin)

        val stateWithError = viewModel.viewState.first()
        assertNotNull("Error message should be present after wrong PIN", stateWithError.errorMessage)

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        val stateAfterDigit = viewModel.viewState.first()
        assertNull("Error message should clear when entering new digits", stateAfterDigit.errorMessage)
        assertEquals("New digit should be added to PIN", "1", stateAfterDigit.enteredPin)
    }

    @Test
    fun `should emit LoggedOut news when logout is clicked`() = runTest {
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnLogoutClick)

        advanceUntilIdle()

        assertEquals(
            "Logout click should emit LoggedOut news",
            VerifyPinCodeNews.LoggedOut,
            collectedNews.firstOrNull(),
        )
    }

    @Test
    fun `account should lock and unlock with correct attempt counts`() = runTest {
        val states = mutableListOf<VerifyPinCodeViewState>()
        viewModel.viewState.onEach { states.add(it) }.launchIn(backgroundScope)

        inputPin(wrongPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val lockedState = states.firstOrNull { it.isLocked && it.remainingAttempts == 0 }
        assertNotNull("A locked state should exist", lockedState)
        assertTrue("Countdown should be active", lockedState!!.lockCountdown > 0)

        advanceTimeBy(31 * 1000)
        advanceUntilIdle()

        val unlockedState = states.lastOrNull()
        assertNotNull("An unlocked state should exist after countdown", unlockedState)
        assertFalse("Account should be unlocked", unlockedState!!.isLocked)
        assertEquals("Attempts should reset to 3", 3, unlockedState.remainingAttempts)
        assertEquals("Countdown should be 0", 0, unlockedState.lockCountdown)
    }

    @Test
    fun `should handle errors during verification`() = runTest {
        pinCodeStorage.setShouldThrowException(true)
        collectNews()

        inputPin(testPin)

        val state = viewModel.viewState.first()
        assertNotNull("Error message should be present when verification fails", state.errorMessage)
        assertTrue("Error message should include error details", state.errorMessage!!.startsWith("Error:"))
        assertFalse("Verification flag should be reset after error", state.isVerifying)

        assertTrue(
            "Error news should be emitted on verification failure",
            collectedNews.first() is VerifyPinCodeNews.Error,
        )
    }

    private fun TestScope.inputPin(pin: String) {
        pin.forEach { digit ->
            viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(digit.toString().toInt()))
        }
        advanceTimeBy(400)
    }

    private fun TestScope.collectNews() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news ->
                collectedNews.add(news)
            }
        }
    }
}
