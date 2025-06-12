package me.sdi.ethwallet.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.security.DataStorePinCodeStorage
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.ProcessLifetimeUserActivityManager
import me.sdi.ethwallet.data.TempFileProvider
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.ui.wallet.CreateWalletViewModel
import me.sdi.ethwallet.ui.wallet.SetPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Application Scope - for app-level background operations
    single<CoroutineScope> { CoroutineScope(SupervisorJob()) }

    // Database
    single<AppDatabase> { AppDatabase.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }

    // Core components
    single<TempFileProvider> { AndroidTempFileProvider(androidContext()) }
    single<EthWalletCreator> { FileBasedEthWalletCreator(get()) }
    single<KeyStoreEncryptor> { KeyStoreEncryptor() }

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
}
