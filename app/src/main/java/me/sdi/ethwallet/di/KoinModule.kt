package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.CryptocompareFiatRateProvider
import me.sdi.ethwallet.core.EthBalanceProvider
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.Web3jEthManager
import me.sdi.ethwallet.core.security.DataStorePinCodeStorage
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.ProcessLifetimeUserActivityManager
import me.sdi.ethwallet.data.TempFileProvider
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.ui.receive.ReceiveQrViewModel
import me.sdi.ethwallet.ui.send.SendViewModel
import me.sdi.ethwallet.ui.wallet.CreateWalletViewModel
import me.sdi.ethwallet.ui.wallet.SetPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.WalletViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import me.sdi.ethwallet.Logger as WalletLogger

val appModule = module {
    // Application Scope - for app-level background operations
    single<CoroutineScope> { CoroutineScope(SupervisorJob()) }

    // Database
    single<AppDatabase> { DatabaseBuilder.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    // Core components
    single<TempFileProvider> { AndroidTempFileProvider(androidContext()) }
    single<EthWalletCreator> { FileBasedEthWalletCreator(get()) }
    single<PrivateKeyEncryptor> { KeyStoreEncryptor() }

    // Web3 and Blockchain
    single { Web3j.build(HttpService("https://eth.llamarpc.com")) }

    single<HttpClient> {
        HttpClient(OkHttp) {
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

    factory<EthManager> { Web3jEthManager(get()) }
    factory<BalanceProvider> { EthBalanceProvider(get(), get()) }
    factory<FiatRateProvider> { CryptocompareFiatRateProvider(get(), get()) }
    factory<TransferInteractor> { TransferInteractor(get(), get(), get(), get()) }

    // Repository
    single<WalletRepository> {
        WalletRepository(
            walletDao = get(),
            walletCreator = get(),
            privateKeyEncryptor = get(),
        )
    }

    // User Activity Manager - Singleton
    single<UserActivityManager> {
        ProcessLifetimeUserActivityManager(
            walletRepository = get(),
            applicationScope = get(),
        )
    }

    // Security
    single<PinCodeStorage> {
        DataStorePinCodeStorage(
            context = androidContext(),
            userActivityManager = get(),
        )
    }

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

    viewModel<WalletViewModel> {
        WalletViewModel(
            walletRepository = get(),
            balanceProvider = get(),
            fiatRateProvider = get(),
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
}
