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
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.ui.InitialNavigationViewModel
import me.sdi.ethwallet.ui.InitialRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNavigationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var viewModel: InitialNavigationViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        pinCodeStorage = FakePinCodeStorage()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_clear_pin_and_show_onboarding_when_no_wallets() =
        runTest(testDispatcher) {
            walletDao.wallets = emptyList()

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            assertEquals(1, pinCodeStorage.clearPinCalledCount)

            // Backward compatibility check
            assertEquals(0, viewModel.numberOfWallets.first())
        }

    @Test
    fun should_set_VerifyPinCodeScreen_as_initial_route_when_wallets_and_pin_exist() =
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

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(2, state.numberOfWallets)
            assertEquals(true, state.isPinCodeSet)
            assertEquals(InitialRoute.VerifyPinCode, state.initialRoute)

            // Backward compatibility check
            assertEquals(2, viewModel.numberOfWallets.first())
        }

    @Test
    fun should_show_onboarding_when_wallets_exist_but_no_pin_invalid_state() =
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

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(1, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)
        }

    @Test
    fun should_handle_exceptions_clear_pin_and_set_onboarding_route() =
        runTest(testDispatcher) {
            walletDao.shouldThrowException = true

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            assertEquals(1, pinCodeStorage.clearPinCalledCount)

            // Backward compatibility check
            assertEquals(0, viewModel.numberOfWallets.first())
        }
}
