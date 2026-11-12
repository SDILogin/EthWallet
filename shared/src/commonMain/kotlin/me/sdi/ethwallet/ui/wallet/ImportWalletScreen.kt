package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
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
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.components.rememberScreenTransition
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthButtonsLayout
import me.sdi.ethwallet.ui.designsystem.EthIconButton
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextField
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val PREVIEW_MNEMONIC = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"

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

    ImportWalletScreenContent(
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onUpButtonClick = onUpButtonClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportWalletScreenContent(
    viewState: ImportWalletViewState,
    onEvent: (ImportWalletEvent) -> Unit,
    onUpButtonClick: () -> Unit,
    modifier: Modifier,
) {
    val transition = rememberScreenTransition()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { EthText(stringResource(Res.string.import_wallet_title)) },
                navigationIcon = {
                    EthIconButton(onClick = { transition.exit(onUpButtonClick) }) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        EthButtonsLayout(
            buttons = {
                EthButton(
                    text = stringResource(Res.string.import_wallet_button),
                    onClick = { onEvent(ImportWalletEvent.ImportClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewState.isImportEnabled,
                    loading = viewState.isLoading,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding(),
            buttonsVisible = transition.contentVisible,
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = transition.fadeAlpha }
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                EthText(
                    text = stringResource(Res.string.import_wallet_enter_recovery_phrase),
                    style = EthTextStyle.Headline,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                EthText(
                    text = stringResource(Res.string.import_wallet_instruction),
                    style = EthTextStyle.Body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                EthTextField(
                    value = viewState.label,
                    onValueChange = { onEvent(ImportWalletEvent.LabelChanged(it)) },
                    label = stringResource(Res.string.wallet_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("wallet-label-input"),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    placeholder = { EthText(stringResource(Res.string.wallet_label_placeholder)) },
                    supportingText = { EthText(stringResource(Res.string.wallet_label_supporting)) },
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

                NoPersonalizedLearningInput {
                    EthTextField(
                        value = viewState.mnemonic,
                        onValueChange = { onEvent(ImportWalletEvent.MnemonicChanged(it)) },
                        label = stringResource(Res.string.import_wallet_recovery_phrase_label),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 420.dp)
                                .semantics { isSensitiveData = true },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        placeholder = { EthText(stringResource(Res.string.import_wallet_recovery_phrase_placeholder)) },
                        supportingText = { EthText(supportingText) },
                        isError = viewState.isError,
                        enabled = !viewState.isLoading,
                        maxLines = 3,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private class ImportWalletViewStateProvider : PreviewParameterProvider<ImportWalletViewState> {
    override val values = sequenceOf(
        ImportWalletViewState(),
        ImportWalletViewState(label = "Savings", mnemonic = PREVIEW_MNEMONIC, isImportEnabled = true),
        ImportWalletViewState(label = "Savings", mnemonic = PREVIEW_MNEMONIC, isImportEnabled = true, isLoading = true),
        ImportWalletViewState(label = "Savings", mnemonic = "word1 word2", isError = true),
    )
}

@FormFactorPreviews
@Composable
private fun ImportWalletScreenContentPreview(
    @PreviewParameter(ImportWalletViewStateProvider::class) viewState: ImportWalletViewState,
) {
    EthWalletTheme {
        ImportWalletScreenContent(
            viewState = viewState,
            onEvent = {},
            onUpButtonClick = {},
            modifier = Modifier,
        )
    }
}
