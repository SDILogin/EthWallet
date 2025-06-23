package me.sdi.ethwallet.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.NetworkManagerConfigurator
import me.sdi.ethwallet.data.ProcessLifetimeUserActivityManager
import me.sdi.ethwallet.data.TempFileProvider
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.ui.receive.ReceiveQrViewModel
import me.sdi.ethwallet.ui.wallet.CreateWalletViewModel
import me.sdi.ethwallet.ui.wallet.SetPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.WalletViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

val appModule = module {
    // Application Scope - for app-level background operations
    single<CoroutineScope> { CoroutineScope(SupervisorJob()) }

    // Database
    single<AppDatabase> { AppDatabase.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    // Core components
    single<TempFileProvider> { AndroidTempFileProvider(androidContext()) }
    single<EthWalletCreator> { FileBasedEthWalletCreator(get()) }
    single<KeyStoreEncryptor> { KeyStoreEncryptor() }

    // Web3 and Blockchain
    single { Web3j.build(HttpService("https://eth.llamarpc.com")) }
    single<NetworkManagerConfigurator> { NetworkManagerConfigurator() }
    factory<EthManager> { Web3jEthManager(get()) }
    factory<BalanceProvider> { EthBalanceProvider(get(), get()) }
    factory<FiatRateProvider> { CryptocompareFiatRateProvider(get(), get()) }
    factory<TransferInteractor> { TransferInteractor(get(), get(), get(), get()) }

    // Repository
    single<WalletRepository> {
        WalletRepository(
            walletDao = get(),
            walletCreator = get(),
            keyStoreEncryptor = get(),
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
}
