package me.sdi.ethwallet.ui.wallet

import android.icu.text.NumberFormat
import android.icu.util.Currency
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.SwitchAccount
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valentinilk.shimmer.shimmer
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.ui.icon
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal

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

    val context = LocalContext.current
    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is WalletNews.ShowErrorMessage -> {
                    val message = context.getString(
                        R.string.wallet_error_format,
                        news.error.message ?: context.getString(R.string.error_unknown),
                    )
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    WalletScreen(
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
fun WalletScreen(
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
                title = { Text(stringResource(R.string.wallet_title)) },
                actions = {
                    IconButton(onClick = onManageWallets) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = stringResource(R.string.wallet_manage_wallets),
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
                title = stringResource(R.string.wallet_receive),
                icon = Icons.Default.ArrowDownward,
                onClick = onReceiveClick,
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        if (isLoading) {
            CircularButtonWithTitleShimmer()
        } else {
            CircularButtonWithTitle(
                title = stringResource(R.string.wallet_send),
                icon = Icons.Default.ArrowOutward,
                onClick = onSendButtonClick,
            )
        }
    }
}

@Composable
private fun CircularButtonWithTitle(
    title: String,
    icon: ImageVector,
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
                imageVector = icon,
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
private fun CircularButtonWithTitleShimmer(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.shimmer(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                ),
        )
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
                        text = stringResource(R.string.wallet_tokens_erc20),
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
                        stringResource(R.string.edit),
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
private fun TotalBalanceShimmer(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shimmer()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main balance shimmer placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        // ETH balance shimmer placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                ),
        )
    }
}

@Composable
private fun TokensCardShimmer(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.shimmer(),
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                        ),
                )

                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
            }

            repeat(3) {
                TokenItemShimmer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TokenItemShimmer(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.shimmer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Token icon shimmer
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp),
                ),
        )

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Token name shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Token symbol shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
        }

        // Token balance shimmer
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 20.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ),
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
                            usdValue = BigDecimal("1.53"),
                            usdValueFormatted = "1.53",
                        )
                    },
                ),
            onEditButtonClick = { },
            onManageWallets = { },
            onTokenClick = { },
            onReceiveButtonClick = { },
            onSendButtonClick = { },
        )
    }
}

@Composable
@PreviewFontScale
@PreviewLightDark
fun WalletScreenLoadingPreview() {
    EthWalletTheme {
        WalletScreen(
            viewState = WalletViewState(
                isLoading = true,
                totalBalanceUsd = "",
                totalBalanceEth = "",
                favoriteTokens = emptyList(),
            ),
            onEditButtonClick = { },
            onManageWallets = { },
            onTokenClick = { },
            onReceiveButtonClick = { },
            onSendButtonClick = { },
        )
    }
}
