package me.sdi.ethwallet.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

fun main() = application {
    val chainConfig = ChainConfig(
        rpcUrl = System.getProperty("eth.rpc.url") ?: "https://eth.llamarpc.com",
        chainId = System.getProperty("eth.chain.id")?.toLongOrNull() ?: 1L,
        explorerBaseUrl = System.getProperty("eth.explorer.base.url"),
        transactionHistoryApiBaseUrl =
            System.getProperty("eth.transaction.history.api.base.url") ?: "https://eth.blockscout.com",
    )

    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules(chainConfig)) }),
        content = {
            val userActivityManager: UserActivityManager = koinInject()
            val userActivity = remember(userActivityManager) { ThrottledUserActivity(userActivityManager) }

            Window(
                onCloseRequest = ::exitApplication,
                title = "EthWallet",
                state = rememberWindowState(),
                onPreviewKeyEvent = {
                    userActivity.report()
                    false
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(userActivity) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                                        userActivity.report()
                                    }
                                }
                            }
                        },
                ) {
                    EthWalletApp()
                }
            }
        },
    )
}

/**
 * Reports input to [UserActivityManager] at most once per [reportInterval], because every report
 * cancels and relaunches the inactivity timer while pointer events can arrive many times a second.
 *
 * Only ever touched from the AWT event thread, so the time mark needs no synchronization.
 */
private class ThrottledUserActivity(private val userActivityManager: UserActivityManager) {
    private val reportInterval = 1.seconds
    private var lastReportedAt: TimeMark? = null

    fun report() {
        val lastReportedAt = lastReportedAt
        if (lastReportedAt != null && lastReportedAt.elapsedNow() < reportInterval) {
            return
        }

        this.lastReportedAt = TimeSource.Monotonic.markNow()
        userActivityManager.onAnyUserAction()
    }
}
