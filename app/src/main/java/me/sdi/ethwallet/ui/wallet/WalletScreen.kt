package me.sdi.ethwallet.ui.wallet

import android.icu.text.NumberFormat
import android.icu.util.Currency
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Composable
fun WalletScreen(
    onManageWallets: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel<WalletViewModel>(
        factory = viewModelFactory {
            initializer {
                val context = checkNotNull(this[APPLICATION_KEY])
                val tempFileProvider = AndroidTempFileProvider(context)
                val walletCreator = FileBasedEthWalletCreator(tempFileProvider)
                val keyStoreEncryptor = KeyStoreEncryptor()
                val database = AppDatabase.getInstance(context)
                val walletRepository = WalletRepository(
                    walletDao = database.walletDao(),
                    walletCreator = walletCreator,
                    keyStoreEncryptor = keyStoreEncryptor,
                )
                WalletViewModel(walletRepository)
            }
        },
    )
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.onEvent(WalletEvent.Initialize)
    }

    val context = LocalContext.current
    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is WalletNews.ShowErrorMessage -> {
                    val message = "Error: ${news.error.message ?: "Unknown error"}"
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    WalletScreen(
        viewState = viewState,
        onManageWallets = onManageWallets,
        onEditButtonClick = onEditButtonClick,
        onTokenClick = onTokenClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewState: WalletViewState,
    onManageWallets: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                actions = {
                    IconButton(onClick = onManageWallets) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Wallets",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                TotalFiatBalanceCard(
                    fiatBalance = viewState.totalBalanceUsd,
                    ethBalance = viewState.totalBalanceEth,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewState.favoriteTokens.isNotEmpty()) {
                    TokensCard(
                        favoriteTokens = viewState.favoriteTokens,
                        onEditButtonClick = onEditButtonClick,
                        onTokenClick = onTokenClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalFiatBalanceCard(
    fiatBalance: String,
    ethBalance: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = fiatBalance,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = 48.sp,
            ),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = ethBalance,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun TokensCard(
    favoriteTokens: List<FavoriteToken>,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    BasicText(
                        text = "Tokens (ERC-20)",
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 12.sp,
                            maxFontSize = 20.sp,
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                TextButton(
                    onClick = { onEditButtonClick() },
                ) {
                    Text(
                        "Edit",
                    )
                }
            }

            favoriteTokens.forEach { token ->
                TokenItem(
                    tokenAddress = token.address,
                    tokenName = token.name,
                    tokenSymbol = token.ticker,
                    tokenBalance = token.amount,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTokenClick(token.address) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TokenItem(
    tokenAddress: EthAddress,
    tokenName: String,
    tokenSymbol: String,
    tokenBalance: String,
    modifier: Modifier = Modifier,
) {
    @DrawableRes
    val tokenIcon: Int = remember(tokenAddress) {
        val token = KnownTokens.entries.firstOrNull { knownToken ->
            knownToken.ethAddress == tokenAddress
        }
        token?.icon ?: R.drawable.ic_question_mark_56
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(48.dp),
            painter = painterResource(tokenIcon),
            contentDescription = null,
            tint = Color.Unspecified,
        )

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tokenName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tokenSymbol,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Text(
            text = tokenBalance,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@PreviewFontScale
@PreviewLightDark
fun WalletScreenPreview() {
    EthWalletTheme {
        WalletScreen(
            viewState =
                WalletViewState(
                    totalBalanceUsd = NumberFormat
                        .getCurrencyInstance()
                        .apply { currency = Currency.getInstance("USD") }
                        .format(1280.75),
                    totalBalanceEth = "0.97 ETH",
                    favoriteTokens = KnownTokens.entries.map { knownToken ->
                        FavoriteToken(
                            address = knownToken.ethAddress,
                            name = knownToken.name,
                            ticker = knownToken.name,
                            amount = "1.53",
                            usdValue = "1.53",
                        )
                    },
                ),
            onEditButtonClick = { },
            onManageWallets = { },
            onTokenClick = { },
        )
    }
}
