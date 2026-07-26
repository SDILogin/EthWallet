package me.sdi.ethwallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakePinSession
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.ui.InitialNavigationEvent
import me.sdi.ethwallet.ui.InitialNavigationViewModel
import me.sdi.ethwallet.ui.InitialRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNavigationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var dataCleaner: FakeDataCleaner
    private lateinit var pinSession: FakePinSession
    private lateinit var viewModel: InitialNavigationViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        pinCodeStorage = FakePinCodeStorage()
        dataCleaner = FakeDataCleaner()
        pinSession = FakePinSession()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        InitialNavigationViewModel(walletDao, pinCodeStorage, dataCleaner, pinSession)

    private fun givenWalletAndPin() {
        walletDao.wallets = listOf(
            WalletEntity(
                address = "0x123abc",
                encryptedPrivateKeyBase64 = "encrypted_key_1",
                label = "Wallet 1",
            ),
        )
        pinCodeStorage.storedPin = "123456"
    }

    @Test
    fun should_clean_data_and_show_onboarding_when_no_wallets() =
        runTest(testDispatcher) {
            walletDao.wallets = emptyList()

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            assertEquals(1, dataCleaner.cleanCalledCount, "No wallets should trigger a data cleanup")
        }

    @Test
    fun should_set_wallet_as_initial_route_when_wallets_and_pin_exist() =
        runTest(testDispatcher) {
            val wallets =
                listOf(
                    WalletEntity(
                        address = "0x123abc",
                        encryptedPrivateKeyBase64 = "encrypted_key_1",
                        label = "Wallet 1",
                    ),
                    WalletEntity(
                        address = "0x456def",
                        encryptedPrivateKeyBase64 = "encrypted_key_2",
                        label = "Wallet 2",
                    ),
                )

            walletDao.wallets = wallets
            pinCodeStorage.storedPin = "123456"

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(2, state.numberOfWallets)
            assertEquals(true, state.isPinCodeSet)
            assertEquals(InitialRoute.Wallet, state.initialRoute)
            assertEquals(0, dataCleaner.cleanCalledCount, "Wallets with a PIN set should not trigger a data cleanup")
        }

    @Test
    fun should_clean_data_and_show_onboarding_when_wallets_exist_but_no_pin_invalid_state() =
        runTest(testDispatcher) {
            val wallets =
                listOf(
                    WalletEntity(
                        address = "0x123abc",
                        encryptedPrivateKeyBase64 = "encrypted_key_1",
                        label = "Wallet 1",
                    ),
                )

            walletDao.wallets = wallets
            pinCodeStorage.storedPin = null

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(1, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)
            assertEquals(1, dataCleaner.cleanCalledCount, "Wallets without a PIN should trigger a data cleanup")
        }

    @Test
    fun should_handle_exceptions_clean_data_and_set_onboarding_route() =
        runTest(testDispatcher) {
            walletDao.shouldThrowException = true

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            assertEquals(1, dataCleaner.cleanCalledCount, "A wallet lookup failure should trigger a data cleanup")
        }

    @Test
    fun should_show_onboarding_when_wallet_lookup_and_cleanup_both_fail() =
        runTest(testDispatcher) {
            walletDao.shouldThrowException = true
            dataCleaner.shouldThrowException = true

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(
                InitialRoute.OnBoarding,
                state.initialRoute,
                "Onboarding should still be reached when cleanup itself fails",
            )
        }

    @Test
    fun should_lock_when_a_pin_is_configured_and_the_session_is_empty() =
        runTest(testDispatcher) {
            givenWalletAndPin()

            viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.isLocked,
                "A restored process holds no session key, so the app must start locked",
            )
        }

    @Test
    fun should_not_lock_when_the_session_already_holds_the_key() =
        runTest(testDispatcher) {
            givenWalletAndPin()
            pinSession.set(byteArrayOf(1, 2, 3, 4))

            viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLocked, "An unlocked session must not show the PIN screen")
        }

    @Test
    fun should_lock_when_the_session_is_cleared_while_the_app_runs() =
        runTest(testDispatcher) {
            givenWalletAndPin()
            pinSession.set(byteArrayOf(1, 2, 3, 4))

            viewModel = createViewModel()
            advanceUntilIdle()

            pinSession.clear()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isLocked, "Clearing the session key locks the app")
        }

    @Test
    fun should_unlock_when_the_session_key_is_set_again() =
        runTest(testDispatcher) {
            givenWalletAndPin()

            viewModel = createViewModel()
            advanceUntilIdle()

            pinSession.set(byteArrayOf(1, 2, 3, 4))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLocked, "Verifying the PIN republishes the key and lifts the lock")
        }

    @Test
    fun should_lock_after_the_pin_is_set_for_the_first_time_and_the_session_expires() =
        runTest(testDispatcher) {
            walletDao.wallets = emptyList()

            viewModel = createViewModel()
            advanceUntilIdle()

            pinSession.set(byteArrayOf(1, 2, 3, 4))
            advanceUntilIdle()
            pinSession.clear()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.isLocked,
                "A PIN set during onboarding must be enough to lock the app when the session expires",
            )
        }

    @Test
    fun should_never_lock_while_no_pin_has_been_set() =
        runTest(testDispatcher) {
            walletDao.wallets = emptyList()

            viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(
                viewModel.state.value.isLocked,
                "Onboarding has no PIN to verify, so the lock screen must stay away",
            )
        }

    @Test
    fun should_unlock_and_show_onboarding_once_the_wallets_are_gone() =
        runTest(testDispatcher) {
            givenWalletAndPin()

            viewModel = createViewModel()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isLocked, "The app starts locked when a PIN is configured")

            viewModel.onEvent(InitialNavigationEvent.WalletsCleared)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLocked, "There is nothing left to protect once the wallets are gone")
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)
        }
}
