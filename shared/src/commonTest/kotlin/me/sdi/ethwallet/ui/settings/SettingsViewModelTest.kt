package me.sdi.ethwallet.ui.settings

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
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.fakes.FakePinSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var biometricUnlock: FakeBiometricUnlock
    private lateinit var pinSession: FakePinSession

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        biometricUnlock = FakeBiometricUnlock()
        pinSession = FakePinSession()
    }

    @AfterTest
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun ready_status_should_show_enabled_switch_with_no_hint() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        assertEquals(
            SettingsViewState(
                isBiometricEnabled = true,
                isBiometricSwitchEnabled = true,
                biometricHint = BiometricSettingsHint.None,
            ),
            viewModel.viewState.value,
            "Ready status should show the switch on and enabled with no hint",
        )
    }

    @Test
    fun not_set_up_status_should_show_disabled_toggle_with_enabled_switch() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        assertEquals(
            SettingsViewState(
                isBiometricEnabled = false,
                isBiometricSwitchEnabled = true,
                biometricHint = BiometricSettingsHint.None,
            ),
            viewModel.viewState.value,
            "NotSetUp status should show the switch off but toggleable with no hint",
        )
    }

    @Test
    fun not_enrolled_status_should_disable_switch_with_hint() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotEnrolled
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        assertEquals(
            SettingsViewState(
                isBiometricEnabled = false,
                isBiometricSwitchEnabled = false,
                biometricHint = BiometricSettingsHint.NotEnrolled,
            ),
            viewModel.viewState.value,
            "NotEnrolled status should disable the switch and show the NotEnrolled hint",
        )
    }

    @Test
    fun not_supported_status_should_disable_switch_with_hint() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotSupported
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        assertEquals(
            SettingsViewState(
                isBiometricEnabled = false,
                isBiometricSwitchEnabled = false,
                biometricHint = BiometricSettingsHint.NotSupported,
            ),
            viewModel.viewState.value,
            "NotSupported status should disable the switch and show the NotSupported hint",
        )
    }

    @Test
    fun invalidated_status_should_reenable_switch_with_hint() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Invalidated
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        assertEquals(
            SettingsViewState(
                isBiometricEnabled = false,
                isBiometricSwitchEnabled = true,
                biometricHint = BiometricSettingsHint.Invalidated,
            ),
            viewModel.viewState.value,
            "Invalidated status should re-enable the switch and show the Invalidated hint",
        )
    }

    @Test
    fun toggle_on_should_call_enable_and_refresh_to_ready() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.OnBiometricToggle(true))
        advanceUntilIdle()

        assertEquals(1, biometricUnlock.enableCallCount, "Toggle on should call enable exactly once")
        assertEquals(
            SettingsViewState(
                isBiometricEnabled = true,
                isBiometricSwitchEnabled = true,
                biometricHint = BiometricSettingsHint.None,
            ),
            viewModel.viewState.value,
            "State should refresh to reflect the enabled biometric unlock",
        )
    }

    @Test
    fun toggle_off_from_ready_should_call_disable_and_refresh_to_disabled() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.OnBiometricToggle(false))
        advanceUntilIdle()

        assertEquals(1, biometricUnlock.disableCallCount, "Toggle off should call disable exactly once")
        assertFalse(viewModel.viewState.value.isBiometricEnabled, "Switch should turn off after disable")
    }

    @Test
    fun toggle_on_with_error_should_keep_switch_disabled() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        biometricUnlock.enableResult = BiometricResult.Error("boom")
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.OnBiometricToggle(true))
        advanceUntilIdle()

        assertEquals(1, biometricUnlock.enableCallCount, "Toggle on should still call enable exactly once")
        assertFalse(viewModel.viewState.value.isBiometricEnabled, "Switch should stay off when enable fails")
    }

    @Test
    fun lock_click_should_end_the_unlocked_session() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        pinSession.set(ByteArray(32))
        val viewModel = createViewModel(AppLockMethod.Pin)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.OnLockClick)
        advanceUntilIdle()

        assertEquals(1, pinSession.clearCallCount, "Lock should clear the PIN session exactly once")
        assertFalse(pinSession.isUnlocked.value, "The app must be locked after the lock button click")
    }

    @Test
    fun password_lock_hides_the_pin_settings_without_querying_biometrics() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        val viewModel = createViewModel(AppLockMethod.Password)
        advanceUntilIdle()

        assertEquals(AppLockMethod.Password, viewModel.viewState.value.appLockMethod, "Desktop has no PIN or biometric settings")
        assertEquals(0, biometricUnlock.statusCallCount, "Biometric status should not be read for the password lock")
    }

    @Test
    fun biometric_status_is_read_when_the_view_state_is_collected() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            biometricUnlock = biometricUnlock,
            pinSession = pinSession,
            appLockMethod = AppLockMethod.Pin,
        )
        advanceUntilIdle()

        assertEquals(0, biometricUnlock.statusCallCount, "Creating the ViewModel should not read the biometric status")

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        advanceUntilIdle()

        assertEquals(1, biometricUnlock.statusCallCount, "Collecting the view state should read the biometric status")
    }

    private fun TestScope.createViewModel(appLockMethod: AppLockMethod): SettingsViewModel =
        SettingsViewModel(biometricUnlock = biometricUnlock, pinSession = pinSession, appLockMethod = appLockMethod)
            .also { viewModel ->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
            }
}
