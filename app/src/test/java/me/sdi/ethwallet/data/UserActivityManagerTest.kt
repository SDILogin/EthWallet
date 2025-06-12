package me.sdi.ethwallet.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UserActivityManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletRepository: WalletRepository
    private lateinit var testScope: TestScope
    private lateinit var userActivityManager: ProcessLifetimeUserActivityManager

    private val testWallet = EthWallet(
        label = "Test Wallet",
        address = EthAddress("0x1234567890123456789012345678901234567890"),
    )

    @Before
    fun setup() {
        walletRepository = mockk()
        testScope = TestScope(UnconfinedTestDispatcher())
        userActivityManager = ProcessLifetimeUserActivityManager(
            walletRepository = walletRepository,
            applicationScope = testScope,
            inactivityTimeout = 1.seconds,
        )
    }

    @Test
    fun `should initially not show lock screen`() = runTest {
        val showLockScreen = userActivityManager.showLockScreen.first()

        assertFalse(showLockScreen, "Lock screen should not be shown initially")
    }

    @Test
    fun `should show lock screen after inactivity timeout when wallets exist`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertTrue(showLockScreen, "Lock screen should be shown after inactivity timeout when wallets exist")
    }

    @Test
    fun `should not show lock screen after inactivity timeout when no wallets exist`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns emptyList()

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when no wallets exist")
    }

    @Test
    fun `should restart timer on user action`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when timer is restarted by user action")
    }

    @Test
    fun `should not restart timer when lock screen is already showing`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAnyUserAction()

        assertTrue(
            userActivityManager.showLockScreen.first(),
            "Lock screen should remain shown when user action occurs while lock screen is visible",
        )
    }

    @Test
    fun `should hide lock screen when app is unlocked`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAppUnlocked()

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should be hidden after app is unlocked")
    }

    @Test
    fun `should restart timer when app is unlocked`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        userActivityManager.onAppUnlocked()
        advanceTimeBy(500.milliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Timer should be restarted when app is unlocked")
    }

    @Test
    fun `should show lock screen again after unlocking and timeout`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        userActivityManager.onAppUnlocked()
        advanceTimeBy(2.seconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertTrue(showLockScreen, "Lock screen should be shown again after unlocking and timeout")
    }

    @Test
    fun `should handle wallet repository exception gracefully`() = runTest {
        coEvery { walletRepository.getAllWallets() } throws RuntimeException("Database error")

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when wallet repository throws exception")
    }

    @Test
    fun `should rethrow CancellationException from wallet repository`() = runTest {
        coEvery { walletRepository.getAllWallets() } throws CancellationException("Cancelled")

        userActivityManager.onAnyUserAction()

        // When CancellationException is thrown from walletRepository.getAllWallets(),
        // it should be rethrown and the timer coroutine should be cancelled.
        // This means the lock screen state should not change from its initial false state
        advanceTimeBy(2.seconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should remain false when timer job is cancelled due to CancellationException")
    }

    @Test
    fun `should cancel previous timer when new timer is started`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        coVerify(exactly = 1) { walletRepository.getAllWallets() }
    }

    @Test
    fun `should use custom inactivity timeout`() = runTest {
        val customTimeout = 5.seconds
        val customUserActivityManager = ProcessLifetimeUserActivityManager(
            walletRepository = walletRepository,
            applicationScope = testScope,
            inactivityTimeout = customTimeout,
        )
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        customUserActivityManager.onAnyUserAction()
        advanceTimeBy(3.seconds)

        val showLockScreenBefore = customUserActivityManager.showLockScreen.first()
        assertFalse(showLockScreenBefore, "Lock screen should not be shown before custom timeout")

        advanceTimeBy(3.seconds)

        val showLockScreenAfter = customUserActivityManager.showLockScreen.first()
        assertTrue(showLockScreenAfter, "Lock screen should be shown after custom timeout")
    }

    @Test
    fun `should handle multiple consecutive user actions`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        repeat(5) {
            userActivityManager.onAnyUserAction()
            advanceTimeBy(200.milliseconds)
        }

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown with consecutive user actions")
    }

    @Test
    fun `should handle multiple unlock calls`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        repeat(3) {
            userActivityManager.onAppUnlocked()
        }

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should remain hidden after multiple unlock calls")
    }

    @Test
    fun `should handle mixed user actions and unlocks`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns listOf(testWallet)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAppUnlocked()
        advanceTimeBy(500.milliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown with mixed actions keeping timer active")
    }
}
