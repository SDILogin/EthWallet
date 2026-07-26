package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetPinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SetPinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var walletRepository: WalletRepository
    private lateinit var biometricUnlock: FakeBiometricUnlock

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        walletRepository = WalletRepository(
            walletDao = FakeWalletDao(),
            ethManager = FakeEthManager(),
            privateKeyEncryptor = FakePrivateKeyEncryptor(),
        )
        biometricUnlock = FakeBiometricUnlock()
        viewModel = SetPinCodeViewModel(pinCodeStorage, walletRepository, biometricUnlock)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_be_empty_with_no_confirmation_mode() = runTest(testDispatcher) {
        val initialState = viewModel.viewState.first()

        assertEquals("", initialState.firstPin, "Initial first PIN should be empty")
        assertEquals("", initialState.secondPin, "Initial second PIN should be empty")
        assertFalse(initialState.isConfirmationMode, "Should not be in confirmation mode initially")
        assertNull(initialState.errorMessage, "Error message should be null initially")
        assertFalse(initialState.isSaving, "Saving state should be false initially")
    }

    @Test
    fun digit_clicks_should_append_to_first_PIN_when_not_in_confirmation_mode() = runTest(testDispatcher) {
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))

        val state = viewModel.viewState.first()
        assertEquals("123", state.firstPin, "First PIN should contain clicked digits")
        assertEquals("", state.secondPin, "Second PIN should still be empty")
        assertFalse(state.isConfirmationMode, "Should still not be in confirmation mode")
    }

    @Test
    fun should_switch_to_confirmation_mode_when_first_PIN_is_complete() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should contain all 6 digits")
        assertEquals("", state.secondPin, "Second PIN should still be empty")
        assertTrue(state.isConfirmationMode, "Should be in confirmation mode")
    }

    @Test
    fun digit_clicks_should_append_to_second_PIN_when_in_confirmation_mode() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }
        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should be intact")
        assertEquals("12", state.secondPin, "Second PIN should contain clicked digits")
        assertTrue(state.isConfirmationMode, "Should be in confirmation mode")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_first_PIN() = runTest(testDispatcher) {
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))
        viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("12", state.firstPin, "Last digit should be removed from first PIN")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_second_PIN_when_in_confirmation_mode() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }

        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should remain intact")
        assertEquals("1", state.secondPin, "Last digit should be removed from second PIN")
    }

    @Test
    fun reset_click_should_clear_second_PIN_and_return_to_first_PIN_entry() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }

        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnResetClick)

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should remain intact")
        assertEquals("", state.secondPin, "Second PIN should be cleared")
        assertFalse(state.isConfirmationMode, "Should not be in confirmation mode anymore")
    }

    @Test
    fun should_emit_PinCodeSet_news_when_PINs_match() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                collector.add(it)
            }
        }

        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }

        advanceUntilIdle()
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should emit PinCodeSet news",
        )
    }

    @Test
    fun should_clear_PINs_and_show_error_when_PINs_dont_match() = runTest(testDispatcher) {
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        }
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.firstPin, "First PIN should be cleared")
        assertEquals("", state.secondPin, "Second PIN should be cleared")
        assertFalse(state.isConfirmationMode, "Should not be in confirmation mode")
        assertEquals("PINs don't match. Try again.", state.errorMessage, "Should show error message")
    }

    @Test
    fun biometric_status_NotSetUp_should_show_offer_and_withhold_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        job.cancel()

        val state = viewModel.viewState.first()
        assertTrue(state.showBiometricOffer, "NotSetUp biometric status should show the biometric offer")
        assertTrue(
            collector.isEmpty(),
            "PinCodeSet should not be emitted before the biometric offer is resolved",
        )
    }

    @Test
    fun biometric_status_NotSupported_should_emit_PinCodeSet_without_offer() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSupported

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        job.cancel()

        assertEquals(SetPinCodeNews.PinCodeSet, collector.single(), "Should emit PinCodeSet news")
        assertFalse(
            viewModel.viewState.first().showBiometricOffer,
            "NotSupported biometric status should never show the offer",
        )
    }

    @Test
    fun storing_a_PIN_should_disable_biometrics_and_offer_them_again() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.Ready

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        job.cancel()

        assertEquals(
            1,
            biometricUnlock.disableCallCount,
            "Storing a PIN rotates the master KEK, so the stale biometric envelope must be destroyed",
        )
        assertTrue(
            viewModel.viewState.first().showBiometricOffer,
            "The offer should re-appear once the stale envelope is gone",
        )
        assertTrue(
            collector.isEmpty(),
            "PinCodeSet should not be emitted before the biometric offer is resolved",
        )
    }

    @Test
    fun accepting_biometric_offer_should_enable_biometrics_and_emit_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        assertTrue(viewModel.viewState.first().showBiometricOffer, "Offer should be showing before it is accepted")

        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, biometricUnlock.enableCallCount, "Accepting the offer should call enable once")
        assertFalse(viewModel.viewState.first().showBiometricOffer, "Offer should be dismissed after accepting")
        assertEquals(SetPinCodeNews.PinCodeSet, collector.single(), "Should emit PinCodeSet news after accepting")
    }

    @Test
    fun double_accepting_biometric_offer_should_enable_biometrics_once() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        assertTrue(viewModel.viewState.first().showBiometricOffer, "Offer should be showing before it is accepted")

        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, biometricUnlock.enableCallCount, "Double tapping the offer should call enable once")
        assertEquals(
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should emit PinCodeSet exactly once after a double tap",
        )
    }

    @Test
    fun accepting_biometric_offer_with_cancelled_enable_should_still_emit_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        biometricUnlock.enableResult = BiometricResult.Cancelled

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        advanceUntilIdle()
        job.cancel()

        assertFalse(
            viewModel.viewState.first().showBiometricOffer,
            "Offer should be dismissed even when enable is cancelled",
        )
        assertEquals(
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should still emit PinCodeSet after a cancelled enable",
        )
    }

    @Test
    fun accepting_biometric_offer_with_enable_error_should_still_emit_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        biometricUnlock.enableResult = BiometricResult.Error("boom")

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        advanceUntilIdle()
        job.cancel()

        assertFalse(
            viewModel.viewState.first().showBiometricOffer,
            "Offer should be dismissed even when enable errors",
        )
        assertEquals(
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should still emit PinCodeSet after a failed enable",
        )
    }

    @Test
    fun skipping_biometric_offer_should_not_enable_biometrics_and_should_emit_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp

        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        enterMatchingPins()
        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferSkip)
        advanceUntilIdle()
        job.cancel()

        assertEquals(0, biometricUnlock.enableCallCount, "Skipping the offer should not call enable")
        assertFalse(viewModel.viewState.first().showBiometricOffer, "Offer should be dismissed after skipping")
        assertEquals(SetPinCodeNews.PinCodeSet, collector.single(), "Should emit PinCodeSet news after skipping")
    }

    @Test
    fun biometric_offer_events_should_be_ignored_while_the_offer_is_hidden() = runTest(testDispatcher) {
        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collector.add(it) }
        }

        viewModel.onEvent(SetPinCodeEvent.OnBiometricOfferAccept)
        advanceUntilIdle()
        job.cancel()

        assertEquals(0, biometricUnlock.enableCallCount, "A hidden offer should never enable biometrics")
        assertTrue(collector.isEmpty(), "A hidden offer should never emit PinCodeSet")
    }

    private fun TestScope.enterMatchingPins() {
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
    }
}
