package me.sdi.ethwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PinLockPolicyTest {
    @Test
    fun lockDuration_should_climb_the_ladder_and_cap_at_the_last_tier() {
        assertEquals(1.seconds, PinLockPolicy.lockDuration(3), "1st lock tier should be 1 second")
        assertEquals(30.seconds, PinLockPolicy.lockDuration(6), "2nd lock tier should be 30 seconds")
        assertEquals(1.minutes, PinLockPolicy.lockDuration(9), "3rd lock tier should be 1 minute")
        assertEquals(5.minutes, PinLockPolicy.lockDuration(12), "4th lock tier should be 5 minutes")
        assertEquals(15.minutes, PinLockPolicy.lockDuration(15), "5th lock tier should be 15 minutes")
        assertEquals(1.hours, PinLockPolicy.lockDuration(18), "6th lock tier should be 1 hour")
        assertEquals(1.hours, PinLockPolicy.lockDuration(21), "Lock duration should cap at 1 hour beyond the last tier")
    }

    @Test
    fun isLockTriggered_should_be_true_only_at_multiples_of_the_attempts_window() {
        assertFalse(PinLockPolicy.isLockTriggered(0), "Zero failures should never trigger a lock")
        assertFalse(PinLockPolicy.isLockTriggered(1), "1 failure should not trigger a lock")
        assertFalse(PinLockPolicy.isLockTriggered(2), "2 failures should not trigger a lock")
        assertTrue(PinLockPolicy.isLockTriggered(3), "3 failures should trigger a lock")
        assertFalse(PinLockPolicy.isLockTriggered(4), "4 failures should not trigger a lock")
        assertFalse(PinLockPolicy.isLockTriggered(5), "5 failures should not trigger a lock")
        assertTrue(PinLockPolicy.isLockTriggered(6), "6 failures should trigger a lock")
        assertFalse(PinLockPolicy.isLockTriggered(7), "7 failures should not trigger a lock")
    }

    @Test
    fun remainingInWindow_should_count_down_within_each_window() {
        assertEquals(3, PinLockPolicy.remainingInWindow(0), "0 failures should leave all 3 attempts in the window")
        assertEquals(2, PinLockPolicy.remainingInWindow(1), "1 failure should leave 2 attempts in the window")
        assertEquals(1, PinLockPolicy.remainingInWindow(2), "2 failures should leave 1 attempt in the window")
        assertEquals(3, PinLockPolicy.remainingInWindow(3), "3 failures should reset the window to 3 attempts")
        assertEquals(2, PinLockPolicy.remainingInWindow(4), "4 failures should leave 2 attempts in the new window")
        assertEquals(1, PinLockPolicy.remainingInWindow(5), "5 failures should leave 1 attempt in the new window")
        assertEquals(3, PinLockPolicy.remainingInWindow(6), "6 failures should reset the window to 3 attempts again")
    }
}
