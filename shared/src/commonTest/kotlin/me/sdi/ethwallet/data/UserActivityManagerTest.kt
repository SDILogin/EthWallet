package me.sdi.ethwallet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.fakes.FakePinSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Wall-clock time ([wallClock]) is advanced independently from the coroutine test scheduler so that
 * a background trip can outlast the inactivity timeout while the in-process timer stays frozen,
 * which is what a device does while the app sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserActivityManagerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val inactivityTimeout = 1.seconds
    private val wallClock = TestTimeSource()

    private lateinit var pinSession: FakePinSession
    private lateinit var testScope: TestScope
    private lateinit var userActivityManager: ProcessLifetimeUserActivityManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        pinSession = FakePinSession()
        testScope = TestScope(testDispatcher)
        userActivityManager = ProcessLifetimeUserActivityManager(
            pinSession = pinSession,
            applicationScope = testScope,
            timeSource = wallClock,
            inactivityTimeout = inactivityTimeout,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun unlock() {
        pinSession.set(byteArrayOf(1, 2, 3, 4))
        userActivityManager.onAppUnlocked()
    }

    @Test
    fun should_clear_pin_session_after_inactivity_timeout() = runTest(testDispatcher) {
        unlock()

        advanceTimeBy(2.seconds.inWholeMilliseconds)

        assertEquals(1, pinSession.clearCallCount, "Inactivity timeout should clear the PIN session")
        assertFalse(pinSession.isUnlocked.value, "The app should be locked after the inactivity timeout")
    }

    @Test
    fun should_keep_pin_session_while_the_user_keeps_interacting() = runTest(testDispatcher) {
        unlock()

        repeat(5) {
            advanceTimeBy(500.milliseconds.inWholeMilliseconds)
            userActivityManager.onAnyUserAction()
        }
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        assertEquals(0, pinSession.clearCallCount, "User interaction should postpone the inactivity timeout")
        assertTrue(pinSession.isUnlocked.value, "The app should stay unlocked while the user interacts with it")
    }

    @Test
    fun should_not_start_the_timer_while_the_app_is_locked() = runTest(testDispatcher) {
        userActivityManager.onAnyUserAction()

        advanceTimeBy(2.seconds.inWholeMilliseconds)

        assertEquals(0, pinSession.clearCallCount, "A locked app has no session to clear")
    }

    @Test
    fun should_clear_pin_session_when_returning_from_background_after_the_timeout() = runTest(testDispatcher) {
        unlock()

        userActivityManager.onAppBackgrounded()
        wallClock += 2.seconds
        userActivityManager.onAppForegrounded()

        assertFalse(
            pinSession.isUnlocked.value,
            "Returning to the foreground after the inactivity timeout should lock the app",
        )
    }

    @Test
    fun should_keep_pin_session_when_returning_from_background_within_the_timeout() = runTest(testDispatcher) {
        unlock()

        userActivityManager.onAppBackgrounded()
        wallClock += 500.milliseconds
        userActivityManager.onAppForegrounded()

        assertTrue(
            pinSession.isUnlocked.value,
            "Returning to the foreground within the inactivity timeout should keep the app unlocked",
        )
        assertEquals(0, pinSession.clearCallCount, "A short trip to the background should not clear the PIN session")
    }

    @Test
    fun should_restart_the_timer_when_returning_from_background_within_the_timeout() = runTest(testDispatcher) {
        unlock()

        advanceTimeBy(900.milliseconds.inWholeMilliseconds)
        userActivityManager.onAppBackgrounded()
        wallClock += 500.milliseconds
        userActivityManager.onAppForegrounded()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        assertTrue(
            pinSession.isUnlocked.value,
            "Coming back to the foreground should restart the inactivity timeout",
        )
    }

    @Test
    fun should_ignore_foreground_without_a_preceding_background() = runTest(testDispatcher) {
        unlock()

        wallClock += 2.seconds
        userActivityManager.onAppForegrounded()

        assertTrue(
            pinSession.isUnlocked.value,
            "Foreground without a recorded background time should not lock the app",
        )
    }

    @Test
    fun should_measure_background_time_again_on_every_background_trip() = runTest(testDispatcher) {
        unlock()

        userActivityManager.onAppBackgrounded()
        wallClock += 900.milliseconds
        userActivityManager.onAppForegrounded()

        userActivityManager.onAppBackgrounded()
        wallClock += 900.milliseconds
        userActivityManager.onAppForegrounded()

        assertTrue(
            pinSession.isUnlocked.value,
            "Two short background trips should not add up to the inactivity timeout",
        )
    }

    @Test
    fun should_not_clear_pin_session_on_app_unlocked() = runTest(testDispatcher) {
        pinSession.set(byteArrayOf(1, 2, 3, 4))

        userActivityManager.onAppUnlocked()

        assertEquals(0, pinSession.clearCallCount, "onAppUnlocked() must not clear the PIN session")
        assertTrue(pinSession.isUnlocked.value, "onAppUnlocked() must leave the app unlocked")
    }

    @Test
    fun should_restart_the_timer_when_the_app_is_unlocked_again() = runTest(testDispatcher) {
        unlock()
        advanceTimeBy(2.seconds.inWholeMilliseconds)
        assertFalse(pinSession.isUnlocked.value, "The app should be locked after the inactivity timeout")

        unlock()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        assertTrue(pinSession.isUnlocked.value, "Unlocking should restart the inactivity timeout")

        advanceTimeBy(1.seconds.inWholeMilliseconds)

        assertFalse(pinSession.isUnlocked.value, "The app should lock again after the restarted timeout")
    }
}
