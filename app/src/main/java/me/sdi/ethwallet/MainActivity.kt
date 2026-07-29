package me.sdi.ethwallet

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.database.DatabaseBuilder
import me.sdi.ethwallet.ui.EthWalletApp
import org.koin.android.ext.android.inject

class MainActivity : FragmentActivity() {
    private val userActivityManager: UserActivityManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        initLogger()

        DatabaseBuilder.getInstance(application)

        splashScreen.exitWithoutAnimation()

        enableEdgeToEdge()
        setContent {
            EthWalletApp()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()

        userActivityManager.onAnyUserAction()
    }
}

/**
 * Drops the system splash screen the moment the first Compose frame is drawn, with no exit
 * animation, leaving the identical [me.sdi.ethwallet.ui.SplashScreen] behind it on screen.
 *
 * Android 12 and later only. Below that the platform ignores the splash screen theme attributes,
 * and asking androidx.core for an exit animation makes it inflate its own splash view, which needs
 * `Theme.SplashScreen` attributes that this app's platform theme does not declare.
 */
private fun SplashScreen.exitWithoutAnimation() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    setOnExitAnimationListener { splashScreenViewProvider ->
        Logger.debug("Splah") { "JC splash screen exit animation started" }
        splashScreenViewProvider.remove()
    }
}

private fun Context.initLogger() {
    Logger.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    Logger.printer = { level: Level, tag: Tag, message: Message ->
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.WARN -> Log.w(tag, message)
        }
    }
    Logger.debug("EthWallet") { "Logger initialized" }
}
