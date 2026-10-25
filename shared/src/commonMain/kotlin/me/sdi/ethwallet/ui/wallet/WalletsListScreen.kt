package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.add_24px
import me.sdi.ethwallet.resources.cancel
import me.sdi.ethwallet.resources.check_24px
import me.sdi.ethwallet.resources.delete
import me.sdi.ethwallet.resources.error_something_went_wrong
import me.sdi.ethwallet.resources.more_vert_24px
import me.sdi.ethwallet.resources.onboarding_create_wallet
import me.sdi.ethwallet.resources.onboarding_import_wallet
import me.sdi.ethwallet.resources.settings_24px
import me.sdi.ethwallet.resources.wallet_label
import me.sdi.ethwallet.resources.wallet_label_error_length
import me.sdi.ethwallet.resources.wallet_label_error_required
import me.sdi.ethwallet.resources.wallet_label_placeholder
import me.sdi.ethwallet.resources.wallet_label_rename_error
import me.sdi.ethwallet.resources.wallet_label_supporting
import me.sdi.ethwallet.resources.wallet_options
import me.sdi.ethwallet.resources.wallet_rename
import me.sdi.ethwallet.resources.wallet_rename_title
import me.sdi.ethwallet.resources.wallet_settings
import me.sdi.ethwallet.resources.wallets_list_add_wallet
import me.sdi.ethwallet.resources.wallets_list_selected_wallet_icon
import me.sdi.ethwallet.resources.wallets_list_title
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.components.shimmer
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WalletsListScreen(
    selectedWalletAddress: EthAddress?,
    onWalletSelected: (EthAddress) -> Unit,
    onWalletDeleted: (EthAddress) -> Unit,
    onImportWallet: () -> Unit,
    onCreateWallet: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WalletsListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val toastManager = rememberToastManager()
    val errorMessage = stringResource(Res.string.error_something_went_wrong)
    val isListDetailLayout = LocalListDetailSceneScope.current != null

    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                is WalletsListNews.ShowErrorMessage -> toastManager.showToast(errorMessage)
                is WalletsListNews.WalletDeleted -> onWalletDeleted(news.address)
            }
        }
    }

    WalletsListScreenContent(
        modifier = modifier,
        uiState = uiState,
        selectedWalletAddress = selectedWalletAddress,
        isListDetailLayout = isListDetailLayout,
        onWalletSelected = onWalletSelected,
        onEvent = viewModel::onEvent,
        onImportWallet = onImportWallet,
        onCreateWallet = onCreateWallet,
        onSettingsClick = onSettingsClick,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WalletsListScreenContent(
    modifier: Modifier,
    uiState: WalletsListUiState,
    selectedWalletAddress: EthAddress?,
    isListDetailLayout: Boolean,
    onWalletSelected: (EthAddress) -> Unit,
    onEvent: (WalletsListEvent) -> Unit,
    onImportWallet: () -> Unit,
    onCreateWallet: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.wallets_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(Res.drawable.settings_24px),
                            contentDescription = stringResource(Res.string.wallet_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.wallets.isNotEmpty()) {
                var isAddWalletMenuExpanded by remember { mutableStateOf(false) }
                val addWalletLabel = stringResource(Res.string.wallets_list_add_wallet)

                Box {
                    ExtendedFloatingActionButton(
                        onClick = { isAddWalletMenuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = addWalletLabel },
                        icon = {
                            Icon(
                                painter = painterResource(Res.drawable.add_24px),
                                contentDescription = null,
                            )
                        },
                        text = { Text(addWalletLabel) },
                    )

                    DropdownMenu(
                        expanded = isAddWalletMenuExpanded,
                        onDismissRequest = { isAddWalletMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.onboarding_create_wallet)) },
                            onClick = {
                                isAddWalletMenuExpanded = false
                                onCreateWallet()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.onboarding_import_wallet)) },
                            onClick = {
                                isAddWalletMenuExpanded = false
                                onImportWallet()
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = innerPadding.calculateTopPadding()),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (uiState.wallets.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Your wallets will be displayed here",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )

                    Text(
                        text = "You can create a new wallet or import an existing one",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .widthIn(max = 320.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = onCreateWallet,
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_create_wallet),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .widthIn(max = 320.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = onImportWallet,
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_import_wallet),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    ),
                ) {
                    items(
                        items = uiState.wallets,
                        key = { item -> item.wallet.address.toString() },
                        contentType = { it::class.qualifiedName },
                    ) { item ->
                        WalletCard(
                            modifier = Modifier.fillMaxWidth(),
                            item = item,
                            isSelected = isListDetailLayout && item.wallet.address == selectedWalletAddress,
                            onWalletClicked = { onWalletSelected(item.wallet.address) },
                            onDeleteClicked = { onEvent(WalletsListEvent.OnDeleteWalletClick(item.wallet.address)) },
                            onRenameClicked = { onEvent(WalletsListEvent.OnRenameWalletClick(item.wallet)) },
                        )
                    }
                }
            }
        }
    }

    uiState.walletLabelEditor?.let { editor ->
        WalletLabelEditorDialog(
            editor = editor,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletCard(
    item: WalletListItemUiState,
    isSelected: Boolean,
    onWalletClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onRenameClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallet = item.wallet

    Card(
        modifier = modifier,
        onClick = onWalletClicked,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = wallet.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    AnimatedVisibility(
                        isSelected,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.check_24px),
                            contentDescription = stringResource(Res.string.wallets_list_selected_wallet_icon),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = wallet.address.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.totalBalanceUsd == null) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .size(width = 56.dp, height = 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmer(),
                    )
                } else {
                    Text(
                        text = item.totalBalanceUsd,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            var isOptionsMenuExpanded by remember { mutableStateOf(false) }
            val optionsDescription = stringResource(Res.string.wallet_options, wallet.label)
            Box {
                IconButton(
                    onClick = { isOptionsMenuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = optionsDescription },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.more_vert_24px),
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = isOptionsMenuExpanded,
                    onDismissRequest = { isOptionsMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.wallet_rename)) },
                        onClick = {
                            isOptionsMenuExpanded = false
                            onRenameClicked()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.delete)) },
                        onClick = {
                            isOptionsMenuExpanded = false
                            onDeleteClicked()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletLabelEditorDialog(
    editor: WalletLabelEditorState,
    onEvent: (WalletsListEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember(editor.address) {
        mutableStateOf(
            TextFieldValue(
                text = editor.draftLabel,
                selection = TextRange(0, editor.draftLabel.length),
            ),
        )
    }
    LaunchedEffect(editor.address) {
        focusRequester.requestFocus()
    }

    val validationMessage = when {
        editor.hasSaveError -> {
            stringResource(Res.string.wallet_label_rename_error)
        }
        editor.normalizedDraftLabel.isEmpty() -> {
            stringResource(Res.string.wallet_label_error_required)
        }
        editor.normalizedDraftLabel.length > MAX_WALLET_LABEL_LENGTH -> {
            stringResource(Res.string.wallet_label_error_length, MAX_WALLET_LABEL_LENGTH)
        }
        else -> {
            stringResource(Res.string.wallet_label_supporting)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) {
                onEvent(WalletsListEvent.OnWalletLabelEditorDismiss)
            }
        },
        title = {
            Column {
                Text(stringResource(Res.string.wallet_rename_title))
                Text(
                    text = editor.address.abbreviated(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onEvent(WalletsListEvent.OnWalletLabelChanged(it.text))
                },
                label = { Text(stringResource(Res.string.wallet_label)) },
                placeholder = { Text(stringResource(Res.string.wallet_label_placeholder)) },
                supportingText = { Text(validationMessage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("wallet-label-editor-input"),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (editor.isSaveEnabled) {
                            onEvent(WalletsListEvent.OnWalletLabelSaveClick)
                        }
                    },
                ),
                singleLine = true,
                enabled = !editor.isSaving,
                isError = editor.hasSaveError || !isWalletLabelValid(editor.normalizedDraftLabel),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(WalletsListEvent.OnWalletLabelSaveClick) },
                enabled = editor.isSaveEnabled,
            ) {
                Text(stringResource(Res.string.wallet_rename))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(WalletsListEvent.OnWalletLabelEditorDismiss) },
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

private fun EthAddress.abbreviated(): String {
    val address = toString()
    return "${address.take(6)}...${address.takeLast(4)}"
}

@FormFactorPreviews
@Composable
private fun WalletsListScreenPreview() {
    MaterialTheme {
        WalletsListScreenContent(
            modifier = Modifier.fillMaxSize(),
            uiState = WalletsListUiState(
                wallets = listOf(
                    WalletListItemUiState(
                        wallet = EthWallet("Business Wallet", EthAddress("0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef")),
                        totalBalanceUsd = "$12,345.67",
                    ),
                    WalletListItemUiState(
                        wallet = EthWallet("Savings Wallet", EthAddress("0x9876543210987654321098765432109876543210")),
                    ),
                ),
                isLoading = false,
            ),
            selectedWalletAddress = null,
            isListDetailLayout = false,
            onWalletSelected = {},
            onEvent = {},
            onImportWallet = {},
            onCreateWallet = {},
            onSettingsClick = {},
        )
    }
}
