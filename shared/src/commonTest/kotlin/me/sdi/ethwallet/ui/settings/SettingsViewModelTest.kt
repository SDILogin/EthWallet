package me.sdi.ethwallet.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var biometricUnlock: FakeBiometricUnlock

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        biometricUnlock = FakeBiometricUnlock()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ready_status_should_show_enabled_switch_with_no_hint() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
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
        val viewModel = SettingsViewModel(biometricUnlock)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.OnBiometricToggle(true))
        advanceUntilIdle()

        assertEquals(1, biometricUnlock.enableCallCount, "Toggle on should still call enable exactly once")
        assertFalse(viewModel.viewState.value.isBiometricEnabled, "Switch should stay off when enable fails")
    }
}
