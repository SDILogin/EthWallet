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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.add_24px
import me.sdi.ethwallet.resources.check_24px
import me.sdi.ethwallet.resources.delete_24px
import me.sdi.ethwallet.resources.error_something_went_wrong
import me.sdi.ethwallet.resources.onboarding_create_wallet
import me.sdi.ethwallet.resources.onboarding_import_wallet
import me.sdi.ethwallet.resources.wallets_list_active_wallet_icon
import me.sdi.ethwallet.resources.wallets_list_add_wallet
import me.sdi.ethwallet.resources.wallets_list_delete_wallet
import me.sdi.ethwallet.resources.wallets_list_title
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsListScreen(
    onWalletSelected: (EthAddress) -> Unit,
    onSelectedWalletDeleted: () -> Unit,
    onImportWallet: () -> Unit,
    onCreateWallet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WalletsListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val toastManager = rememberToastManager()
    val errorMessage = stringResource(Res.string.error_something_went_wrong)

    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                is WalletScreenNews.ShowWalletDetails -> onWalletSelected(news.ethAddress)
                is WalletScreenNews.ShowErrorMessage -> toastManager.showToast(errorMessage)
                is WalletScreenNews.SelectedWalletDeleted -> onSelectedWalletDeleted()
            }
        }
    }

    WalletsListScreenContent(
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onImportWallet = onImportWallet,
        onCreateWallet = onCreateWallet,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WalletsListScreenContent(
    modifier: Modifier,
    uiState: WalletsListUiState,
    onEvent: (WalletsListEvent) -> Unit,
    onImportWallet: () -> Unit,
    onCreateWallet: () -> Unit,
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
                    modifier = Modifier.fillMaxSize(),
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
                            .widthIn(max = 320.dp)
                            .padding(horizontal = 16.dp),
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
                            .widthIn(max = 320.dp)
                            .padding(horizontal = 16.dp),
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
                        key = { wallet -> wallet.address.toString() },
                        contentType = { it::class.qualifiedName },
                    ) { wallet ->
                        WalletCard(
                            modifier = Modifier.fillMaxWidth(),
                            wallet = wallet,
                            isActive = wallet.address == uiState.selected?.address,
                            onWalletClicked = { onEvent(WalletsListEvent.OnWalletSelected(wallet.address)) },
                            onDeleteClicked = { onEvent(WalletsListEvent.OnDeleteWalletClick(wallet.address)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletCard(
    wallet: EthWallet,
    isActive: Boolean,
    onWalletClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        isActive,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.check_24px),
                            contentDescription = stringResource(Res.string.wallets_list_active_wallet_icon),
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
                Text(
                    text = "$0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onDeleteClicked,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.delete_24px),
                    contentDescription = stringResource(Res.string.wallets_list_delete_wallet),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun WalletsListScreenPreview() {
    MaterialTheme {
        WalletsListScreenContent(
            modifier = Modifier.fillMaxSize(),
            uiState = WalletsListUiState(
                selected = EthWallet("Personal Wallet", EthAddress("0x1234567890123456789012345678901234567890")),
                wallets = listOf(
                    EthWallet("Business Wallet", EthAddress("0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef")),
                    EthWallet("Savings Wallet", EthAddress("0x9876543210987654321098765432109876543210")),
                ),
                isLoading = false,
            ),
            onEvent = {},
            onImportWallet = {},
            onCreateWallet = {},
        )
    }
}
