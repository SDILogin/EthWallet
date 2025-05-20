package me.sdi.ethwallet.ui.wallet

import android.icu.text.NumberFormat
import android.icu.util.Currency
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewState: WalletViewState,
    onManageWallets: () -> Unit,
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
                    TokensCard(favoriteTokens = viewState.favoriteTokens)
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Tokens",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            favoriteTokens.forEachIndexed { index, token ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                TokenItem(
                    tokenName = token.name,
                    tokenSymbol = token.ticker,
                    tokenBalance = token.formattedBalance,
                    tokenIconBackground = Color(token.iconBackground),
                    tokenIconText = token.iconText,
                )
            }
        }
    }
}

@Composable
fun TokenItem(
    tokenName: String,
    tokenSymbol: String,
    tokenBalance: String,
    tokenIconBackground: Color,
    tokenIconText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Token Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tokenIconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tokenIconText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        // Token info
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

        // Token balance
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
                    favoriteTokens = listOf(
                        FavoriteToken(
                            address = "0xdac17f958d2ee523a2206206994597c13d831ec7",
                            name = "Tether",
                            ticker = "USDT",
                            amount = "1.53",
                            usdValue = "1.53",
                        ),
                        FavoriteToken(
                            address = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                            name = "USD Coin",
                            ticker = "USDC",
                            amount = "2.100",
                            usdValue = "2.10",
                        ),
                        FavoriteToken(
                            address = "0x6b175474e89094c44da98b954eedeac495271d0f",
                            name = "Dai",
                            ticker = "DAI",
                            amount = "5",
                            usdValue = "5.00",
                        ),
                    ),
                ),
            onManageWallets = {},
        )
    }
}
