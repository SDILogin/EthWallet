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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.WalletEntity
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
            val database = AppDatabase.getInstance(applicationContext)
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
                            .takeIf { message -> message.isNullOrBlank() }
                            ?: "Something went wrong"

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
                title = { Text("My Wallets") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(WalletsListEvent.OnBackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
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
            } else if (uiState.wallets.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No wallets found",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.wallets) { wallet ->
                        WalletCard(
                            modifier = Modifier.fillMaxWidth(),
                            wallet = wallet,
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
    wallet: WalletEntity,
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
                        wallet.isActive,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Wallet Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = wallet.address,
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
                    contentDescription = "Delete wallet",
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
                wallets = listOf(),
                isLoading = false,
            ),
            WalletsListUiState(
                wallets =
                    listOf(
                        WalletEntity(
                            address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                            encryptedPrivateKeyBase64 = "",
                            label = "My Wallet",
                            isActive = true,
                        ),
                        WalletEntity(
                            address = "0x71C7656EC7ab88b11223efB751B7401B5f6d8976F",
                            encryptedPrivateKeyBase64 = "",
                            label = "My secondary wallet",
                            isActive = false,
                        ),
                    ),
                isLoading = false,
            ),
            WalletsListUiState(
                wallets = emptyList(),
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
