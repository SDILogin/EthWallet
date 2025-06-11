package me.sdi.ethwallet

import android.app.Application
import me.sdi.ethwallet.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EthWalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@EthWalletApplication)
            modules(appModule)
        }
    }
}
