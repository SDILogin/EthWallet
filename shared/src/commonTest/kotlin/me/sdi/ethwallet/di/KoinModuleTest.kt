package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.history.TransfersHistoryProvider
import me.sdi.ethwallet.core.history.TransfersHistoryProviderBlockscout
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.MasterKekStorage
import me.sdi.ethwallet.core.security.PasswordInteractor
import me.sdi.ethwallet.core.security.PasswordKekStorage
import me.sdi.ethwallet.core.security.PasswordStrengthChecker
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakeMasterKekStorage
import me.sdi.ethwallet.fakes.FakePasswordKekStorage
import me.sdi.ethwallet.fakes.FakePasswordStrengthChecker
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.ui.settings.ChangePinCodeViewModel
import me.sdi.ethwallet.ui.settings.SettingsViewModel
import me.sdi.ethwallet.ui.wallet.PasswordUnlockViewModel
import me.sdi.ethwallet.ui.wallet.SetPasswordViewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KoinModuleTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun checkCommonModuleExists() {
        assertNotNull(commonModule)
    }

    @Test
    fun checkSettingsViewModelWiresToRegisteredBiometricUnlock() {
        val fakeBiometricUnlock = FakeBiometricUnlock().apply { currentStatus = BiometricStatus.Ready }
        val koin = koinApplication {
            modules(
                commonModule,
                module {
                    single<BiometricUnlock> { fakeBiometricUnlock }
                    single { AppLockMethod.Pin }
                },
            )
        }.koin

        assertSame(
            fakeBiometricUnlock,
            koin.get<BiometricUnlock>(),
            "BiometricUnlock singleton should resolve to the registered instance",
        )

        val settingsViewModel = koin.get<SettingsViewModel>()
        runTest(testDispatcher) {
            backgroundScope.launch { settingsViewModel.viewState.collect {} }
            advanceUntilIdle()

            assertTrue(
                settingsViewModel.viewState.value.isBiometricEnabled,
                "SettingsViewModel should read its status from the BiometricUnlock instance wired by Koin",
            )
        }
    }

    @Test
    fun checkChangePinCodeViewModelResolvesFromCommonModule() {
        val pinCodeStorage = FakePinCodeStorage()
        val koin = koinApplication {
            modules(
                commonModule,
                module {
                    single<PinCodeStorage> { pinCodeStorage }
                    single<MasterKekStorage> { FakeMasterKekStorage() }
                    single<PinAttemptsGuard> { FakePinAttemptsGuard() }
                },
            )
        }.koin

        assertNotNull(
            koin.get<ChangePinCodeViewModel>(),
            "Opening Change PIN should resolve its ViewModel through the PIN interactor",
        )
    }

    @Test
    fun desktopPasswordScreensResolveWithoutPinBindings() {
        val koin = koinApplication {
            modules(
                commonModule,
                module {
                    single { AppLockMethod.Password }
                    single<BiometricUnlock> { FakeBiometricUnlock() }
                    single<PasswordKekStorage> { FakePasswordKekStorage() }
                    single<PasswordStrengthChecker> { FakePasswordStrengthChecker() }
                    single<DataCleaner> { FakeDataCleaner() }
                },
            )
        }.koin

        assertNotNull(koin.get<SettingsViewModel>(), "Desktop Settings should resolve without PIN bindings")
        assertNotNull(koin.get<SetPasswordViewModel>(), "Password setup should resolve without PIN bindings")
        assertNotNull(koin.get<PasswordUnlockViewModel>(), "Password unlock should resolve without PIN bindings")
        assertNotNull(koin.get<PasswordInteractor>(), "Splash should resolve the password interactor")
    }

    @Test
    fun transactionHistoryProvider_uses_blockscout_implementation() {
        val chainConfig = ChainConfig(
            rpcUrl = "https://rpc.example.test",
            chainId = 1L,
            explorerBaseUrl = "https://explorer.example.test",
            transactionHistoryApiBaseUrl = "https://history.example.test",
        )
        val koin = koinApplication {
            modules(
                commonModule,
                module {
                    single { chainConfig }
                    single { HttpClient(MockEngine { error("Unexpected network request") }) }
                },
            )
        }.koin

        assertIs<TransfersHistoryProviderBlockscout>(koin.get<TransfersHistoryProvider>())
    }
}
