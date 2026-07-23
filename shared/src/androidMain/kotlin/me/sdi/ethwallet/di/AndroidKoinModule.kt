package me.sdi.ethwallet.di

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.Web3jEthManager
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerAndroidKeyStore
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerDataStore
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.core.security.DataStorePinCodeStorage
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.core.transfer.Web3jTransferInteractor
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

actual fun platformModule(chainConfig: ChainConfig): Module = module {
    single<AppDatabase> { DatabaseBuilder.getInstance(androidContext()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    single<PrivateKeyEncryptor> { KeyStoreEncryptor() }

    factory<DataCleaner> {
        DataCleanerComposite(
            listOf(
                DataCleanerRoomDb(get<AppDatabase>()),
                DataCleanerDataStore(get<Context>()),
                DataCleanerAndroidKeyStore(),
            ),
        )
    }

    single { chainConfig }
    single { Web3j.build(HttpService(get<ChainConfig>().rpcUrl)) }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    factory<EthManager> { Web3jEthManager(get(), get()) }
    factory<TransferInteractor> { Web3jTransferInteractor(get(), get(), get(), get(), get()) }

    single<PinCodeStorage> {
        DataStorePinCodeStorage(
            context = androidContext(),
            userActivityManager = get(),
        )
    }
}
