package me.sdi.ethwallet.ui.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.history_refresh_error
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HistoryRoute(
    walletAddress: EthAddress,
    walletLabel: String,
    onBackPressed: () -> Unit,
) {
    val viewModel: HistoryViewModel = koinViewModel(parameters = { parametersOf(walletAddress) })
    val chainConfig: ChainConfig = koinInject()
    val viewState by viewModel.viewState.collectAsState()
    val toastManager = rememberToastManager()
    val refreshErrorMessage = stringResource(Res.string.history_refresh_error)

    LaunchedEffect(viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                HistoryNews.ShowRefreshError -> toastManager.showToast(refreshErrorMessage)
            }
        }
    }

    HistoryScreen(
        modifier = Modifier.fillMaxSize(),
        walletLabel = walletLabel,
        chainId = chainConfig.chainId,
        viewState = viewState,
        onRetry = { viewModel.onEvent(HistoryUserAction.RetryInitialLoad) },
        onRefresh = { viewModel.onEvent(HistoryUserAction.Refresh) },
        onBackPressed = onBackPressed,
        onHistoryItemClick = { _, _ -> },
    )
}
