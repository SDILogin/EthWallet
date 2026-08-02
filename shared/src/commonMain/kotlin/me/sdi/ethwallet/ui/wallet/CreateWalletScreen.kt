package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_confirm_button
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_dismiss_button
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_message
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_title
import me.sdi.ethwallet.resources.create_wallet_next_button_text
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_copied_message
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_instruction
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_title
import me.sdi.ethwallet.resources.create_wallet_security_warning
import me.sdi.ethwallet.resources.create_wallet_title
import me.sdi.ethwallet.resources.warning_24px
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen that displays the mnemonic phrase for a newly created wallet.
 */
@Composable
fun CreateWalletScreen(
    onMnemonicSaved: () -> Unit,
    onUpButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreateWalletViewModel = koinViewModel()
    val viewState by viewModel.viewState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                CreateWalletNews.WalletCreated -> onMnemonicSaved()
            }
        }
    }
    CreateWalletScreenContent(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onUpButtonClick = onUpButtonClick,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreateWalletScreenContent(
    viewState: CreateWalletViewState,
    onEvent: (CreateWalletEvent) -> Unit,
    onUpButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.create_wallet_title)) },
                navigationIcon = {
                    IconButton(onClick = onUpButtonClick) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            )
        },
    ) { innerPadding ->
        val scope = rememberCoroutineScope()
        var showConfirmationDialog by remember { mutableStateOf(false) }
        val copiedMessage = stringResource(Res.string.create_wallet_recovery_phrase_copied_message)
        if (showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmationDialog = false },
                title = { Text(stringResource(Res.string.create_wallet_confirmation_dialog_title)) },
                text = { Text(stringResource(Res.string.create_wallet_confirmation_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmationDialog = false
                            onEvent(CreateWalletEvent.SeedPhraseSaved)
                        },
                    ) {
                        Text(stringResource(Res.string.create_wallet_confirmation_dialog_confirm_button))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showConfirmationDialog = false
                        },
                    ) {
                        Text(stringResource(Res.string.create_wallet_confirmation_dialog_dismiss_button))
                    }
                },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            BasicText(
                text = stringResource(Res.string.create_wallet_recovery_phrase_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(8.dp))

            BasicText(
                modifier = Modifier.widthIn(max = 420.dp),
                text = stringResource(Res.string.create_wallet_recovery_phrase_instruction),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(8.dp))
            MnemonicCard(
                seedPhrase = viewState.seedPhrase,
                modifier =
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                onCopied = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = copiedMessage,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            SecurityWarning()
            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.weight(1f))
            NextButton(
                onClick = {
                    showConfirmationDialog = true
                },
            )
        }
    }
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.create_wallet_next_button_text),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SecurityWarning() {
    Card(
        modifier =
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.warning_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.create_wallet_security_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@FormFactorPreviews
@Composable
private fun CreateWalletScreenContentPreview() {
    EthWalletTheme {
        CreateWalletScreenContent(
            viewState = CreateWalletViewState(
                seedPhrase = listOf(
                    "word1", "word2", "word3",
                    "word4", "word5", "word6",
                    "word7", "word8", "word9",
                    "word10", "word11", "word12",
                ),
            ),
            onEvent = {},
            onUpButtonClick = {},
        )
    }
}
