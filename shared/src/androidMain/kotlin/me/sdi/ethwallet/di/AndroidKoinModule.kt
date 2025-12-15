package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.Web3jEthManager
import me.sdi.ethwallet.core.security.DataStorePinCodeStorage
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.TempFileProvider
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

actual fun platformModule(): Module = module {
    single<AppDatabase> { DatabaseBuilder.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    single<TempFileProvider> { AndroidTempFileProvider(androidContext()) }
    single<EthWalletCreator> { FileBasedEthWalletCreator(get()) }
    single<PrivateKeyEncryptor> { KeyStoreEncryptor() }

    single { Web3j.build(HttpService("https://eth.llamarpc.com")) }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    factory<EthManager> { Web3jEthManager(get()) }
    factory<TransferInteractor> { TransferInteractor(get(), get(), get(), get()) }

    single<PinCodeStorage> {
        DataStorePinCodeStorage(
            context = androidContext(),
            userActivityManager = get(),
        )
    }
}
