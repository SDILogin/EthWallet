package me.sdi.ethwallet.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.util.CurrentActivityHolder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBiometricUnlockTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val userActivityManager = NoOpUserActivityManager()

    private fun newBiometricUnlock(pinSession: PinSession = InMemoryPinSession()) =
        AndroidBiometricUnlock(
            context = context,
            userActivityManager = userActivityManager,
            pinSession = pinSession,
            activityHolder = CurrentActivityHolder(),
            dispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun statusWithoutEnrolledBiometricsDoesNotCrashAndIsNotReady() = runTest {
        val status = newBiometricUnlock().status()

        assertTrue("Without enrolled biometrics status must not be Ready", status != BiometricStatus.Ready)
    }

    @Test
    fun disableWithNothingSetUpCompletes() = runTest {
        newBiometricUnlock().disable()
    }

    @Test
    fun enableWithEmptyPinSessionReturnsErrorWithoutPrompt() = runTest {
        val result = newBiometricUnlock(pinSession = InMemoryPinSession()).enable()

        assertTrue(
            "enable() with no unlocked session must fail without showing a prompt",
            result is BiometricResult.Error,
        )
    }
}

private class NoOpUserActivityManager : UserActivityManager {
    override fun onAnyUserAction() {}

    override fun onAppUnlocked() {}

    override fun onAppBackgrounded() {}

    override fun onAppForegrounded() {}
}
