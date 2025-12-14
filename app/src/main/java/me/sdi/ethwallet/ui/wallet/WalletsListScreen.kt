package me.sdi.ethwallet.ui.wallet

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.DatabaseBuilder
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsListScreen(
    modifier: Modifier = Modifier,
    navigateToOnboarding: () -> Unit,
    navigateToWalletDetails: () -> Unit,
) {
    val applicationContext = LocalContext.current.applicationContext
    val viewModel =
        remember {
            val database = DatabaseBuilder.getInstance(applicationContext)
            val walletDao = database.walletDao()
            val tempFileProvider = AndroidTempFileProvider(applicationContext)
            val ethWalletCreator = FileBasedEthWalletCreator(tempFileProvider)
            val keyStoreEncryptor = KeyStoreEncryptor()
            val walletRepository = WalletRepository(walletDao, ethWalletCreator, keyStoreEncryptor)
            WalletsListViewModel(walletRepository)
        }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.news.collect { news ->
            when (news) {
                WalletScreenNews.NavigateToOnBoarding -> navigateToOnboarding()
                WalletScreenNews.NavigateToWalletDetails -> navigateToWalletDetails()
                is WalletScreenNews.ShowErrorMessage -> {
                    val message =
                        news.error
                            .localizedMessage
                            .takeIf { message -> !message.isNullOrBlank() }
                            ?: applicationContext.getString(R.string.error_something_went_wrong)

                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onEvent(WalletsListEvent.Start)
    }
    WalletsListScreen(modifier, uiState, viewModel::onEvent)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WalletsListScreen(
    modifier: Modifier,
    uiState: WalletsListUiState,
    onEvent: (WalletsListEvent) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallets_list_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(WalletsListEvent.OnBackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
            } else if (uiState.otherWallets.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.wallets_list_no_wallets),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val allWallets by remember {
                    derivedStateOf {
                        (listOfNotNull(uiState.activeWallet) + uiState.otherWallets).sortedBy { it.label }
                    }
                }
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.wallets_list_active_wallet_icon),
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
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.wallets_list_delete_wallet),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private class UiStateProvider : PreviewParameterProvider<WalletsListUiState> {
    override val values =
        sequenceOf(
            WalletsListUiState(
                otherWallets = listOf(),
                isLoading = false,
            ),
            WalletsListUiState(
                activeWallet = EthWallet(
                    address = EthAddress("0x71C7656EC7ab88b11223efB751B7403333133326F"),
                    label = "My Wallet",
                ),
                otherWallets =
                    listOf(
                        EthWallet(
                            address = EthAddress("0x71C7656EC7ab88b11223efB751B7403333133326F"),
                            label = "Other wallet #1",
                        ),
                        EthWallet(
                            address = EthAddress("0x71C7656EC7ab88b11223efB751B7403333133326F"),
                            label = "Other wallet #2",
                        ),
                    ),
                isLoading = false,
            ),
            WalletsListUiState(
                otherWallets = emptyList(),
                isLoading = true,
            ),
        )
}

@Composable
@PreviewLightDark
@PreviewFontScale
private fun WalletsListScreenPreview(
    @PreviewParameter(UiStateProvider::class) uiState: WalletsListUiState,
) {
    EthWalletTheme {
        WalletsListScreen(Modifier.fillMaxSize(), uiState) {}
    }
}
