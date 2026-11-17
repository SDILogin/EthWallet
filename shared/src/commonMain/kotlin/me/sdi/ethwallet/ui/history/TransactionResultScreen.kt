package me.sdi.ethwallet.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.history.Transfer
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionResultScreen(
    transfer: Transfer,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TransactionResultViewModel = koinViewModel(parameters = { parametersOf(transfer) })
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val chainConfig: ChainConfig = koinInject()
    TransactionDetailsScreen(
        state = state,
        walletAddress = transfer.from,
        chainConfig = chainConfig,
        onBackPressed = onBackPressed,
        modifier = modifier,
    )
}
