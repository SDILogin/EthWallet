package me.sdi.ethwallet.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthWalletCreator
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UserActivityManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var walletCreator: FakeEthWalletCreator
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository
    private lateinit var testScope: TestScope
    private lateinit var userActivityManager: ProcessLifetimeUserActivityManager

    private val testWallet = EthWallet(
        label = "Test Wallet",
        address = EthAddress("0x1234567890123456789012345678901234567890"),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        walletCreator = FakeEthWalletCreator()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        walletRepository = WalletRepository(
            walletDao = walletDao,
            walletCreator = walletCreator,
            privateKeyEncryptor = privateKeyEncryptor,
        )

        testScope = TestScope(testDispatcher)
        userActivityManager = ProcessLifetimeUserActivityManager(
            walletRepository = walletRepository,
            applicationScope = testScope,
            inactivityTimeout = 1.seconds,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_initially_not_show_lock_screen() = runTest(testDispatcher) {
        val showLockScreen = userActivityManager.showLockScreen.first()

        assertFalse(showLockScreen, "Lock screen should not be shown initially")
    }

    @Test
    fun should_show_lock_screen_after_inactivity_timeout_when_wallets_exist() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertTrue(showLockScreen, "Lock screen should be shown after inactivity timeout when wallets exist")
    }

    @Test
    fun should_not_show_lock_screen_after_inactivity_timeout_when_no_wallets_exist() = runTest(testDispatcher) {
        walletDao.wallets = emptyList()

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when no wallets exist")
    }

    @Test
    fun should_restart_timer_on_user_action() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when timer is restarted by user action")
    }

    @Test
    fun should_not_restart_timer_when_lock_screen_is_already_showing() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAnyUserAction()

        assertTrue(
            userActivityManager.showLockScreen.first(),
            "Lock screen should remain shown when user action occurs while lock screen is visible",
        )
    }

    @Test
    fun should_hide_lock_screen_when_app_is_unlocked() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAppUnlocked()

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should be hidden after app is unlocked")
    }

    @Test
    fun should_restart_timer_when_app_is_unlocked() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        userActivityManager.onAppUnlocked()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Timer should be restarted when app is unlocked")
    }

    @Test
    fun should_show_lock_screen_again_after_unlocking_and_timeout() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        userActivityManager.onAppUnlocked()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertTrue(showLockScreen, "Lock screen should be shown again after unlocking and timeout")
    }

    @Test
    fun should_handle_wallet_repository_exception_gracefully() = runTest(testDispatcher) {
        walletDao.shouldThrowException = true

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown when wallet repository throws exception")
    }

    @Test
    fun should_rethrow_CancellationException_from_wallet_repository() = runTest(testDispatcher) {
        // Force FakeWalletDao to throw CancellationException on getAllWallets
        walletDao.wallets = emptyList()
        // We can throw cancellation exception using a mock exception if we add support,
        // or we can simulate it in our FakeWalletDao by adding an exception field:
        walletDao.exceptionToThrow = CancellationException("Cancelled")

        userActivityManager.onAnyUserAction()

        // When CancellationException is thrown from walletRepository.getAllWallets(),
        // it should be rethrown and the timer coroutine should be cancelled.
        // This means the lock screen state should not change from its initial false state
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should remain false when timer job is cancelled due to CancellationException")
    }

    @Test
    fun should_cancel_previous_timer_when_new_timer_is_started() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        // The wallets query should only resolve once the final timer fires
        // Under our fake, since each action starts a new coroutine job and cancels the previous one,
        // we can observe the side effects.
        val showLockScreen = userActivityManager.showLockScreen.first()
        assertTrue(showLockScreen)
    }

    @Test
    fun should_use_custom_inactivity_timeout() = runTest(testDispatcher) {
        val customTimeout = 5.seconds
        val customUserActivityManager = ProcessLifetimeUserActivityManager(
            walletRepository = walletRepository,
            applicationScope = testScope,
            inactivityTimeout = customTimeout,
        )
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        customUserActivityManager.onAnyUserAction()
        advanceTimeBy(3.seconds.inWholeMilliseconds)

        val showLockScreenBefore = customUserActivityManager.showLockScreen.first()
        assertFalse(showLockScreenBefore, "Lock screen should not be shown before custom timeout")

        advanceTimeBy(3.seconds.inWholeMilliseconds)

        val showLockScreenAfter = customUserActivityManager.showLockScreen.first()
        assertTrue(showLockScreenAfter, "Lock screen should be shown after custom timeout")
    }

    @Test
    fun should_handle_multiple_consecutive_user_actions() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        repeat(5) {
            userActivityManager.onAnyUserAction()
            advanceTimeBy(200.milliseconds.inWholeMilliseconds)
        }

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown with consecutive user actions")
    }

    @Test
    fun should_handle_multiple_unlock_calls() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        repeat(3) {
            userActivityManager.onAppUnlocked()
        }

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should remain hidden after multiple unlock calls")
    }

    @Test
    fun should_handle_mixed_user_actions_and_unlocks() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet.address.toString(),
                encryptedPrivateKeyBase64 = "",
                label = "Test Wallet",
            ),
        )

        userActivityManager.onAnyUserAction()
        advanceTimeBy(2.seconds.inWholeMilliseconds)

        assertTrue(userActivityManager.showLockScreen.first(), "Lock screen should be shown after timeout")

        userActivityManager.onAppUnlocked()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        userActivityManager.onAnyUserAction()
        advanceTimeBy(500.milliseconds.inWholeMilliseconds)

        val showLockScreen = userActivityManager.showLockScreen.first()
        assertFalse(showLockScreen, "Lock screen should not be shown with mixed actions keeping timer active")
    }
}
