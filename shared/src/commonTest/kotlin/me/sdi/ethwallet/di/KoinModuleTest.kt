package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.history.TransactionHistoryProvider
import me.sdi.ethwallet.core.history.TransactionHistoryProviderBlockscout
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.ui.settings.SettingsViewModel
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
                module { single<BiometricUnlock> { fakeBiometricUnlock } },
            )
        }.koin

        assertSame(
            fakeBiometricUnlock,
            koin.get<BiometricUnlock>(),
            "BiometricUnlock singleton should resolve to the registered instance",
        )

        val settingsViewModel = koin.get<SettingsViewModel>()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            settingsViewModel.viewState.value.isBiometricEnabled,
            "SettingsViewModel should read its status from the BiometricUnlock instance wired by Koin",
        )
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

        assertIs<TransactionHistoryProviderBlockscout>(koin.get<TransactionHistoryProvider>())
    }
}
