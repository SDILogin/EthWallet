package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_confirm_button
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_dismiss_button
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_message
import me.sdi.ethwallet.resources.create_wallet_confirmation_dialog_title
import me.sdi.ethwallet.resources.create_wallet_error_title
import me.sdi.ethwallet.resources.create_wallet_loading_message
import me.sdi.ethwallet.resources.create_wallet_next_button_text
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_copied_message
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_instruction
import me.sdi.ethwallet.resources.create_wallet_recovery_phrase_title
import me.sdi.ethwallet.resources.create_wallet_security_warning
import me.sdi.ethwallet.resources.create_wallet_title
import me.sdi.ethwallet.resources.create_wallet_unknown_error_message
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen that displays the mnemonic phrase for a newly created wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    onMnemonicSaved: () -> Unit,
    onUpButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreateWalletViewModel = koinViewModel()
    val viewState by viewModel.viewState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.create_wallet_title)) },
                navigationIcon = {
                    IconButton(onClick = onUpButtonClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
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
        CreateWalletScreenContent(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            viewState = viewState,
            onEvent = { event -> viewModel.onEvent(event) },
            onMnemonicSaved = onMnemonicSaved,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun CreateWalletScreenContent(
    viewState: CreateWalletViewState,
    onEvent: (CreateWalletEvent) -> Unit,
    onMnemonicSaved: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(Unit) {
        onEvent(CreateWalletEvent.CreateWallet)
    }

    Crossfade(
        modifier = modifier,
        targetState = viewState::class.qualifiedName,
    ) { target ->
        when (target) {
            CreateWalletViewState.Loading::class.qualifiedName -> LoadingState()
            CreateWalletViewState.Content::class.qualifiedName -> {
                (viewState as? CreateWalletViewState.Content)?.let { state ->
                    ContentState(
                        state = state,
                        onMnemonicSaved = onMnemonicSaved,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }

            CreateWalletViewState.Error::class.qualifiedName -> {
                (viewState as? CreateWalletViewState.Error)?.throwable?.let { throwable ->
                    ErrorState(throwable)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(Res.string.create_wallet_loading_message))
        }
    }
}

@Composable
private fun ContentState(
    state: CreateWalletViewState.Content,
    onMnemonicSaved: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
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
                        onMnemonicSaved()
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
            seedPhrase = state.seedPhrase,
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
                imageVector = Icons.Rounded.Warning,
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

@Composable
private fun ErrorState(throwable: Throwable) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.create_wallet_error_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = throwable.message ?: stringResource(Res.string.create_wallet_unknown_error_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
