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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.check_24px
import me.sdi.ethwallet.resources.delete_24px
import me.sdi.ethwallet.resources.error_something_went_wrong
import me.sdi.ethwallet.resources.wallets_list_active_wallet_icon
import me.sdi.ethwallet.resources.wallets_list_delete_wallet
import me.sdi.ethwallet.resources.wallets_list_no_wallets
import me.sdi.ethwallet.resources.wallets_list_title
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsListScreen(
    modifier: Modifier = Modifier,
    navigateToOnboarding: () -> Unit,
    navigateToWalletDetails: () -> Unit,
    viewModel: WalletsListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val toastManager = rememberToastManager()
    val errorMessage = stringResource(Res.string.error_something_went_wrong)

    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                WalletScreenNews.NavigateToOnBoarding -> {
                    navigateToOnboarding()
                }
                WalletScreenNews.NavigateToWalletDetails -> {
                    navigateToWalletDetails()
                }
                is WalletScreenNews.ShowErrorMessage -> {
                    val message = news.error.message?.takeIf { it.isNotBlank() } ?: errorMessage
                    toastManager.showToast(message)
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onEvent(WalletsListEvent.Start)
    }
    WalletsListScreenContent(modifier, uiState, viewModel::onEvent)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WalletsListScreenContent(
    modifier: Modifier,
    uiState: WalletsListUiState,
    onEvent: (WalletsListEvent) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.wallets_list_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(WalletsListEvent.OnBackPress) }) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (uiState.activeWallet == null && uiState.otherWallets.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.wallets_list_no_wallets),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val allWallets by remember {
                    derivedStateOf {
                        (listOfNotNull(uiState.activeWallet) + uiState.otherWallets).sortedBy { it.label }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = allWallets,
                        key = { wallet -> wallet.address.toString() },
                        contentType = { it::class.qualifiedName },
                    ) { wallet ->
                        WalletCard(
                            modifier = Modifier.fillMaxWidth(),
                            wallet = wallet,
                            isActive = wallet.address == uiState.activeWallet?.address,
                            onWalletClicked = { onEvent(WalletsListEvent.SetActiveWallet(wallet.address)) },
                            onDeleteClicked = { onEvent(WalletsListEvent.DeleteWallet(wallet.address)) },
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
                activeWallet = EthWallet("Personal Wallet", EthAddress("0x1234567890123456789012345678901234567890")),
                otherWallets = listOf(
                    EthWallet("Business Wallet", EthAddress("0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef")),
                    EthWallet("Savings Wallet", EthAddress("0x9876543210987654321098765432109876543210")),
                ),
                isLoading = false,
            ),
            onEvent = {},
        )
    }
}
