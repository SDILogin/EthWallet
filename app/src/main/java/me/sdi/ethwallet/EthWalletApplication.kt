package me.sdi.ethwallet

import android.app.Application
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.util.AppForegroundTracker
import me.sdi.ethwallet.util.CurrentActivityHolder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EthWalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val chainConfig = ChainConfig(
            rpcUrl = BuildConfig.ETH_RPC_URL,
            chainId = BuildConfig.ETH_CHAIN_ID,
            explorerBaseUrl = BuildConfig.ETH_EXPLORER_BASE_URL,
        )

        val koinApplication = startKoin {
            androidLogger()
            androidContext(this@EthWalletApplication)
            modules(appModules(chainConfig))
        }

        registerActivityLifecycleCallbacks(koinApplication.koin.get<CurrentActivityHolder>())
        registerActivityLifecycleCallbacks(koinApplication.koin.get<AppForegroundTracker>())
    }
}
