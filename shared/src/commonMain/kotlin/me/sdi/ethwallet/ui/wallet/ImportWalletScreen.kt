package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.import_wallet_button
import me.sdi.ethwallet.resources.import_wallet_enter_recovery_phrase
import me.sdi.ethwallet.resources.import_wallet_instruction
import me.sdi.ethwallet.resources.import_wallet_invalid_recovery_phrase
import me.sdi.ethwallet.resources.import_wallet_recovery_phrase_label
import me.sdi.ethwallet.resources.import_wallet_recovery_phrase_placeholder
import me.sdi.ethwallet.resources.import_wallet_recovery_phrase_supporting
import me.sdi.ethwallet.resources.import_wallet_title
import me.sdi.ethwallet.resources.wallet_label
import me.sdi.ethwallet.resources.wallet_label_placeholder
import me.sdi.ethwallet.resources.wallet_label_supporting
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    onWalletImported: () -> Unit,
    onUpButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ImportWalletViewModel = koinViewModel()
    val viewState by viewModel.viewState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                ImportWalletNews.WalletImported -> onWalletImported()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_wallet_title)) },
                navigationIcon = {
                    IconButton(onClick = onUpButtonClick) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.import_wallet_enter_recovery_phrase),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.import_wallet_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = viewState.label,
                onValueChange = { viewModel.onEvent(ImportWalletEvent.LabelChanged(it)) },
                label = { Text(stringResource(Res.string.wallet_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag("wallet-label-input"),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                placeholder = { Text(stringResource(Res.string.wallet_label_placeholder)) },
                supportingText = { Text(stringResource(Res.string.wallet_label_supporting)) },
                isError = viewState.label.isNotEmpty() && !isWalletLabelValid(normalizeWalletLabel(viewState.label)),
                enabled = !viewState.isLoading,
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val supportingText =
                if (viewState.isError) {
                    stringResource(Res.string.import_wallet_invalid_recovery_phrase)
                } else {
                    stringResource(Res.string.import_wallet_recovery_phrase_supporting)
                }

            OutlinedTextField(
                value = viewState.mnemonic,
                onValueChange = { viewModel.onEvent(ImportWalletEvent.MnemonicChanged(it)) },
                label = { Text(stringResource(Res.string.import_wallet_recovery_phrase_label)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                placeholder = { Text(stringResource(Res.string.import_wallet_recovery_phrase_placeholder)) },
                supportingText = { Text(supportingText) },
                isError = viewState.isError,
                enabled = !viewState.isLoading,
                maxLines = 3,
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.onEvent(ImportWalletEvent.ImportClicked) },
                modifier =
                    Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = viewState.isImportEnabled && !viewState.isLoading,
            ) {
                if (viewState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                } else {
                    Text(
                        text = stringResource(Res.string.import_wallet_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
