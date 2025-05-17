package me.sdi.ethwallet.ui.wallet

import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import java.net.SocketTimeoutException

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
    val viewModel: CreateWalletViewModel = viewModel(factory = CreateWalletViewModel.Factory)
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet") },
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
        CreateWalletScreen(
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
private fun CreateWalletScreen(
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
            Text(text = "Creating your Ethereum wallet...")
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
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Confirm") },
            text = { Text("Are you sure you've written down your recovery phrase? You won't be able to see it again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        onMnemonicSaved()
                    },
                ) {
                    Text("Yes, I've stored it securely")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmationDialog = false
                    },
                ) {
                    Text("No, let me check again")
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
            text = "Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.height(8.dp))

        BasicText(
            modifier = Modifier.widthIn(max = 420.dp),
            text = "Write down these 12 words in order and keep them somewhere safe.",
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Recovery phrase copied to clipboard",
                            duration = SnackbarDuration.Short,
                        )
                    }
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
            text = "I've safely stored my recovery phrase",
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
                text =
                    "WARNING: Never share your recovery phrase! " +
                        "Taking screenshots or storing it digitally may expose it to hackers. " +
                        "Write it down on paper and keep it in a secure location.",
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
            text = "Error Creating Wallet",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = throwable.message ?: "An unknown error occurred",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private class ViewStateProvider : PreviewParameterProvider<CreateWalletViewState> {
    override val values =
        sequenceOf(
            CreateWalletViewState.Loading,
            CreateWalletViewState.Error(SocketTimeoutException()),
            CreateWalletViewState.Content(
                seedPhrase =
                    listOf(
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                        "avocado",
                    ),
            ),
        )
}

@Composable
@PreviewScreenSizes
@PreviewLightDark
@PreviewFontScale
private fun CreateWalletScreenPreview(
    @PreviewParameter(ViewStateProvider::class) viewState: CreateWalletViewState,
) {
    EthWalletTheme {
        CreateWalletScreen(
            viewState = viewState,
            onEvent = { },
            onMnemonicSaved = { },
            modifier = Modifier.fillMaxSize(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
