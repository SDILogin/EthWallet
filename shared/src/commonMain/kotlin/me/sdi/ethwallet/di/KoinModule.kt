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
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthBalanceProvider
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.EthManagerKotlin
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.FiatRateProviderBinance
import me.sdi.ethwallet.core.JsonRpcTransportKtor
import me.sdi.ethwallet.core.history.HistoryUpdateInteractor
import me.sdi.ethwallet.core.history.HistoryUpdateInteractorDefault
import me.sdi.ethwallet.core.history.TransactionHistoryProvider
import me.sdi.ethwallet.core.history.TransactionHistoryProviderBlockscout
import me.sdi.ethwallet.core.history.TransactionHistoryRepository
import me.sdi.ethwallet.core.history.TransactionHistoryRepositoryRoom
import me.sdi.ethwallet.core.security.InMemoryPinSession
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.data.ProcessLifetimeUserActivityManager
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.kotlin.EthereumNetwork
import me.sdi.ethwallet.kotlin.JsonRpcTransport
import me.sdi.ethwallet.ui.receive.ReceiveQrViewModel
import me.sdi.ethwallet.ui.send.SendViewModel
import me.sdi.ethwallet.ui.settings.SettingsViewModel
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

    single<JsonRpcTransport> { JsonRpcTransportKtor(get(), get<ChainConfig>().rpcUrl) }
    single { EthereumNetwork(get<ChainConfig>().chainId, get()) }
    single<EthManager> { EthManagerKotlin(get()) }
    single<TransactionHistoryProvider> {
        TransactionHistoryProviderBlockscout(
            httpClient = get(),
            baseUrl = get<ChainConfig>().transactionHistoryApiBaseUrl,
        )
    }
    single<TransactionHistoryRepository> {
        TransactionHistoryRepositoryRoom(
            transactionHistoryDao = get(),
            chainConfig = get(),
        )
    }
    factory<HistoryUpdateInteractor> {
        HistoryUpdateInteractorDefault(
            historyProvider = get(),
            historyRepository = get(),
        )
    }

    factory<WalletRepository> {
        WalletRepository(
            walletDao = get(),
            ethManager = get(),
            privateKeyEncryptor = get(),
        )
    }

    single<PinSession> { InMemoryPinSession() }

    single<UserActivityManager> {
        ProcessLifetimeUserActivityManager(
            pinSession = get(),
            applicationScope = get(),
        )
    }

    factory<BalanceProvider> { EthBalanceProvider(get(), get()) }
    factory<FiatRateProvider> { FiatRateProviderBinance(get(), get()) }

    viewModel<SetPinCodeViewModel> {
        SetPinCodeViewModel(
            pinCodeStorage = get(),
            walletRepository = get(),
            biometricUnlock = get(),
        )
    }

    viewModel<VerifyPinCodeViewModel> {
        VerifyPinCodeViewModel(
            pinCodeStorage = get(),
            dataCleaner = get(),
            pinAttemptsGuard = get(),
            biometricUnlock = get(),
        )
    }

    viewModel<SettingsViewModel> {
        SettingsViewModel(
            biometricUnlock = get(),
            pinSession = get(),
        )
    }

    viewModel<CreateWalletViewModel> {
        CreateWalletViewModel(
            ethManager = get(),
            walletRepository = get(),
        )
    }

    viewModel<ImportWalletViewModel> {
        ImportWalletViewModel(
            walletRepository = get(),
        )
    }

    viewModel<WalletViewModel> { params ->
        WalletViewModel(
            walletAddress = params.get(),
            balanceProvider = get(),
            fiatRateProvider = get(),
            chainConfig = get(),
            walletRepository = get(),
        )
    }

    viewModel<ReceiveQrViewModel> { params ->
        ReceiveQrViewModel(
            walletAddress = params.get(),
        )
    }

    viewModel<SendViewModel> { params ->
        SendViewModel(
            walletAddress = params.get(),
            transferInteractor = get(),
            fiatRateProvider = get(),
            walletRepository = get(),
            savedStateHandle = get(),
        )
    }

    viewModel<WalletsListViewModel> {
        WalletsListViewModel(
            walletRepository = get(),
            balanceProvider = get(),
            fiatRateProvider = get(),
            chainConfig = get(),
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
