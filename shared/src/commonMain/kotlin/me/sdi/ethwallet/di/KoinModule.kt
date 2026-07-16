package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.Bip44EthWalletCreator
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.CryptocompareFiatRateProvider
import me.sdi.ethwallet.core.EthBalanceProvider
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.ProcessLifetimeUserActivityManager
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.InitialNavigationViewModel
import me.sdi.ethwallet.ui.receive.ReceiveQrViewModel
import me.sdi.ethwallet.ui.send.SendViewModel
import me.sdi.ethwallet.ui.wallet.CreateWalletViewModel
import me.sdi.ethwallet.ui.wallet.ImportWalletViewModel
import me.sdi.ethwallet.ui.wallet.SetPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.WalletViewModel
import me.sdi.ethwallet.ui.wallet.WalletsListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import me.sdi.ethwallet.Logger as WalletLogger

expect fun platformModule(chainConfig: ChainConfig): Module

val commonModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob()) }

    single<EthWalletCreator> { Bip44EthWalletCreator() }

    single<WalletRepository> {
        WalletRepository(
            walletDao = get(),
            walletCreator = get(),
            privateKeyEncryptor = get(),
        )
    }

    single<UserActivityManager> {
        ProcessLifetimeUserActivityManager(
            walletRepository = get(),
            applicationScope = get(),
        )
    }

    factory<BalanceProvider> { EthBalanceProvider(get(), get()) }
    factory<FiatRateProvider> { CryptocompareFiatRateProvider(get(), get()) }

    // ViewModels
    viewModel<InitialNavigationViewModel> {
        InitialNavigationViewModel(
            walletDao = get(),
            pinCodeStorage = get(),
        )
    }

    viewModel<SetPinCodeViewModel> {
        SetPinCodeViewModel(
            pinCodeStorage = get(),
        )
    }

    viewModel<VerifyPinCodeViewModel> {
        VerifyPinCodeViewModel(
            pinCodeStorage = get(),
        )
    }

    viewModel<CreateWalletViewModel> {
        CreateWalletViewModel(
            walletRepository = get(),
        )
    }

    viewModel<ImportWalletViewModel> {
        ImportWalletViewModel(
            walletRepository = get(),
        )
    }

    viewModel<WalletViewModel> {
        WalletViewModel(
            walletRepository = get(),
            balanceProvider = get(),
            fiatRateProvider = get(),
            chainConfig = get(),
        )
    }

    viewModel<ReceiveQrViewModel> {
        ReceiveQrViewModel(
            walletRepository = get(),
        )
    }

    viewModel<SendViewModel> {
        SendViewModel(
            transferInteractor = get(),
        )
    }

    viewModel<WalletsListViewModel> {
        WalletsListViewModel(
            walletRepository = get(),
        )
    }
}

fun createHttpClient(engine: io.ktor.client.engine.HttpClientEngine): HttpClient {
    return HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    WalletLogger.debug("KtorHttpClient") { message }
                }
            }
            level = LogLevel.ALL
        }
    }
}

fun appModules(chainConfig: ChainConfig): List<Module> = listOf(commonModule, platformModule(chainConfig))
