package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_downward_24px
import me.sdi.ethwallet.resources.arrow_outward_24px
import me.sdi.ethwallet.resources.edit
import me.sdi.ethwallet.resources.ic_question_mark_56
import me.sdi.ethwallet.resources.switch_account_24px
import me.sdi.ethwallet.resources.wallet_manage_wallets
import me.sdi.ethwallet.resources.wallet_network
import me.sdi.ethwallet.resources.wallet_receive
import me.sdi.ethwallet.resources.wallet_send
import me.sdi.ethwallet.resources.wallet_title
import me.sdi.ethwallet.resources.wallet_tokens_erc20
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.ui.components.shimmer
import me.sdi.ethwallet.ui.icon
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WalletScreen(
    onManageWallets: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    onReceiveButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalletViewModel = koinViewModel<WalletViewModel>(),
) {
    LaunchedEffect(viewModel) {
        viewModel.onEvent(WalletEvent.Initialize)
    }

    val toastManager = rememberToastManager()
    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is WalletNews.ShowErrorMessage -> {
                    toastManager.showToast("Error: ${news.error.message ?: "Unknown error"}")
                }
            }
        }
    }

    val viewState by viewModel.viewState.collectAsState()
    WalletScreenContent(
        viewState = viewState,
        onManageWallets = onManageWallets,
        onEditButtonClick = onEditButtonClick,
        onTokenClick = onTokenClick,
        onReceiveButtonClick = onReceiveButtonClick,
        onSendButtonClick = onSendButtonClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreenContent(
    viewState: WalletViewState,
    onManageWallets: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    onReceiveButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.wallet_title)) },
                actions = {
                    IconButton(onClick = onManageWallets) {
                        Icon(
                            painter = painterResource(Res.drawable.switch_account_24px),
                            contentDescription = stringResource(Res.string.wallet_manage_wallets),
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

                if (viewState.isLoading) {
                    TotalBalanceShimmer()
                } else {
                    TotalFiatBalanceCard(
                        fiatBalance = viewState.totalBalanceUsd,
                        ethBalance = viewState.totalBalanceEth,
                        chainId = viewState.chainId,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ButtonsPanel(
                    isLoading = viewState.isLoading,
                    onReceiveClick = onReceiveButtonClick,
                    onSendButtonClick = onSendButtonClick,
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (viewState.isLoading) {
                    TokensCardShimmer()
                } else if (viewState.favoriteTokens.isNotEmpty()) {
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
private fun ButtonsPanel(
    isLoading: Boolean,
    onReceiveClick: () -> Unit,
    onSendButtonClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularButtonWithTitleShimmer()
        } else {
            CircularButtonWithTitle(
                title = stringResource(Res.string.wallet_receive),
                icon = painterResource(Res.drawable.arrow_downward_24px),
                onClick = onReceiveClick,
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        if (isLoading) {
            CircularButtonWithTitleShimmer()
        } else {
            CircularButtonWithTitle(
                title = stringResource(Res.string.wallet_send),
                icon = painterResource(Res.drawable.arrow_outward_24px),
                onClick = onSendButtonClick,
            )
        }
    }
}

@Composable
private fun CircularButtonWithTitle(
    title: String,
    icon: Painter,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun CircularButtonWithTitleShimmer() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmer(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer(),
        )
    }
}

@Composable
private fun TotalFiatBalanceCard(
    fiatBalance: String,
    ethBalance: String,
    chainId: Long,
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.wallet_network, chainId.toNetworkName()),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

private fun Long.toNetworkName(): String {
    return if (this == 1L) {
        "Mainnet"
    } else {
        "Testnet"
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
                        text = stringResource(Res.string.wallet_tokens_erc20),
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
                        stringResource(Res.string.edit),
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
    val tokenIcon: DrawableResource = remember(tokenAddress) {
        val token = KnownTokens.entries.firstOrNull { knownToken ->
            knownToken.ethAddress == tokenAddress
        }
        token?.icon ?: Res.drawable.ic_question_mark_56
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
private fun TotalBalanceShimmer(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(8.dp))
                .height(56.dp)
                .shimmer(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(8.dp))
                .height(24.dp)
                .shimmer(),
        )
    }
}

@Composable
private fun TokensCardShimmer() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(width = 60.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer(),
            )

            repeat(3) {
                TokenItemShimmer()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TokenItemShimmer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmer(),
        )

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer(),
            )
        }

        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer(),
        )
    }
}

@Preview
@Composable
private fun WalletScreenContentPreview() {
    MaterialTheme {
        WalletScreenContent(
            viewState = WalletViewState(
                chainId = 11155111L,
                totalBalanceUsd = "$12,345.67",
                totalBalanceEth = "5.234 ETH",
                favoriteTokens = listOf(
                    FavoriteToken(
                        address = KnownTokens.USDT.ethAddress,
                        name = "Tether USD",
                        ticker = "USDT",
                        amount = "1,250.00",
                        usdValue = java.math.BigDecimal("1250.00"),
                        usdValueFormatted = "$1,250.00",
                    ),
                    FavoriteToken(
                        address = KnownTokens.USDC.ethAddress,
                        name = "USD Coin",
                        ticker = "USDC",
                        amount = "500.00",
                        usdValue = java.math.BigDecimal("500.00"),
                        usdValueFormatted = "$500.00",
                    ),
                    FavoriteToken(
                        address = KnownTokens.DAI.ethAddress,
                        name = "Dai Stablecoin",
                        ticker = "DAI",
                        amount = "750.50",
                        usdValue = java.math.BigDecimal("750.50"),
                        usdValueFormatted = "$750.50",
                    ),
                ),
                isLoading = false,
            ),
            onManageWallets = {},
            onEditButtonClick = {},
            onTokenClick = {},
            onReceiveButtonClick = {},
            onSendButtonClick = {},
        )
    }
}

@Preview
@Composable
private fun WalletScreenContentShimmerPreview() {
    MaterialTheme {
        WalletScreenContent(
            viewState = WalletViewState(
                isLoading = true,
            ),
            onManageWallets = {},
            onEditButtonClick = {},
            onTokenClick = {},
            onReceiveButtonClick = {},
            onSendButtonClick = {},
        )
    }
}
