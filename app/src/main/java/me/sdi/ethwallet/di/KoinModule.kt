package me.sdi.ethwallet.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    // Application Context - provided by Koin Android
    single<Context> { androidContext() }

    // Application Scope - for app-level background operations
    single<CoroutineScope> { CoroutineScope(SupervisorJob()) }
}
