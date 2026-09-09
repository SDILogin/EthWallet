package me.sdi.ethwallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.security.AndroidBiometricUnlock
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerAndroidKeyStore
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerDataStore
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.core.security.KeyStorePinAttemptsGuard
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinCodeStorageDataStore
import me.sdi.ethwallet.core.security.PinSessionDataCleaner
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.core.transfer.EthTransferInteractor
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.TransactionHistoryDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.util.AppForegroundTracker
import me.sdi.ethwallet.util.CurrentActivityHolder
import me.sdi.ethwallet.util.preferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(chainConfig: ChainConfig): Module = module {
    single<DataStore<Preferences>> { androidContext().preferencesDataStore }
    single<AppDatabase> { DatabaseBuilder.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }
    single<TransactionHistoryDao> { get<AppDatabase>().transactionHistoryDao() }

    single<PrivateKeyEncryptor> { KeyStoreEncryptor(pinSession = get()) }

    factory<DataCleaner> {
        DataCleanerComposite(
            listOf(
                DataCleanerRoomDb(get<AppDatabase>()),
                DataCleanerDataStore(get<Context>()),
                DataCleanerAndroidKeyStore(),
                PinSessionDataCleaner(get()),
            ),
        )
    }

    single { chainConfig }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    factory<TransferInteractor> { EthTransferInteractor(get(), get(), get(), get()) }

    single<PinAttemptsGuard> { KeyStorePinAttemptsGuard(context = androidContext()) }

    single<PinCodeStorage> {
        PinCodeStorageDataStore(
            context = androidContext(),
            userActivityManager = get(),
            pinAttemptsGuard = get(),
            pinSession = get(),
        )
    }

    single { CurrentActivityHolder() }

    single { AppForegroundTracker(userActivityManager = get()) }

    single<BiometricUnlock> {
        AndroidBiometricUnlock(
            context = androidContext(),
            userActivityManager = get(),
            pinSession = get(),
            activityHolder = get(),
        )
    }
}
