package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.KeyStoreEncryptor
import me.sdi.ethwallet.core.TransferInteractor
import me.sdi.ethwallet.core.Web3jEthManager
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import org.koin.core.module.Module
import org.koin.dsl.module
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

actual fun platformModule(chainConfig: ChainConfig): Module = module {
    single<AppDatabase> { DesktopDatabaseBuilder.getInstance() }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    single<PrivateKeyEncryptor> { KeyStoreEncryptor() }

    single { chainConfig }
    single { Web3j.build(HttpService(get<ChainConfig>().rpcUrl)) }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    factory<EthManager> { Web3jEthManager(get()) }
    factory<TransferInteractor> { TransferInteractor(get(), get(), get(), get()) }

    single<PinCodeStorage> { DesktopPinCodeStorage() }
}

private class DesktopPinCodeStorage : PinCodeStorage {
    override suspend fun storePinCode(pinCode: String) {
        // Desktop implementation placeholder
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        return true
    }

    override suspend fun hasPinCode(): Boolean {
        return false
    }

    override suspend fun clearPinCode() {
        // Desktop implementation placeholder
    }
}
