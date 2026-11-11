package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.arrow_downward_24px
import me.sdi.ethwallet.resources.arrow_outward_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.edit
import me.sdi.ethwallet.resources.history_24px
import me.sdi.ethwallet.resources.history_title
import me.sdi.ethwallet.resources.ic_question_mark_56
import me.sdi.ethwallet.resources.wallet_balance_error_message
import me.sdi.ethwallet.resources.wallet_balance_error_title
import me.sdi.ethwallet.resources.wallet_fiat_unavailable
import me.sdi.ethwallet.resources.wallet_network
import me.sdi.ethwallet.resources.wallet_receive
import me.sdi.ethwallet.resources.wallet_refresh_error
import me.sdi.ethwallet.resources.wallet_retry
import me.sdi.ethwallet.resources.wallet_send
import me.sdi.ethwallet.resources.wallet_tokens_erc20
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.components.shimmer
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.designsystem.EthButton
import me.sdi.ethwallet.ui.designsystem.EthButtonStyle
import me.sdi.ethwallet.ui.designsystem.EthCard
import me.sdi.ethwallet.ui.designsystem.EthCardTone
import me.sdi.ethwallet.ui.designsystem.EthIconButton
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.designsystem.EthTextStyle
import me.sdi.ethwallet.ui.findKnownToken
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TOKEN_SHIMMER_COUNT = 6
private const val TWO_COLUMNS_MIN_WIDTH_DP = 600
private const val THREE_COLUMNS_MIN_WIDTH_DP = 900

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WalletScreen(
    walletAddress: EthAddress,
    onUpButtonClick: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    onReceiveButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    onHistoryButtonClick: (walletLabel: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WalletViewModel = koinViewModel(
        parameters = { parametersOf(walletAddress) },
    )
    val toastManager = rememberToastManager()
    val showUpButton = LocalListDetailSceneScope.current == null
    val refreshErrorMessage = stringResource(Res.string.wallet_refresh_error)
    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                WalletNews.ShowErrorMessage -> toastManager.showToast(refreshErrorMessage)
            }
        }
    }

    val viewState by viewModel.viewState.collectAsState()
    WalletScreenContent(
        viewState = viewState,
        onRefresh = { viewModel.onEvent(WalletEvent.Refresh) },
        onRetry = { viewModel.onEvent(WalletEvent.Retry) },
        showUpButton = showUpButton,
        onUpButtonClick = onUpButtonClick,
        onEditButtonClick = onEditButtonClick,
        onTokenClick = onTokenClick,
        onReceiveButtonClick = onReceiveButtonClick,
        onSendButtonClick = onSendButtonClick,
        onHistoryButtonClick = { onHistoryButtonClick(viewState.walletName) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreenContent(
    viewState: WalletViewState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    showUpButton: Boolean,
    onUpButtonClick: () -> Unit,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    onReceiveButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    onHistoryButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { EthText(text = viewState.walletName, style = EthTextStyle.Title) },
                navigationIcon = if (showUpButton) {
                    {
                        EthIconButton(onClick = onUpButtonClick) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    }
                } else {
                    {}
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = viewState.showP2rIndicator,
            onRefresh = onRefresh,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    when (viewState.balanceLoadState) {
                        WalletBalanceLoadState.Error -> BalanceErrorCard(onRetry = onRetry)
                        WalletBalanceLoadState.Loading,
                        WalletBalanceLoadState.Content,
                        -> TotalFiatBalanceCard(
                            modifier = Modifier.fillMaxWidth(),
                            fiatBalance = viewState.totalBalanceUsd,
                            ethBalance = viewState.totalBalanceEth,
                            chainId = viewState.chainId,
                            balanceLoadState = viewState.balanceLoadState,
                            fiatLoadState = viewState.fiatLoadState,
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ButtonsPanel(
                        isLoading = viewState.balanceLoadState == WalletBalanceLoadState.Loading,
                        isSendEnabled = viewState.balanceLoadState == WalletBalanceLoadState.Content,
                        onReceiveClick = onReceiveButtonClick,
                        onSendButtonClick = onSendButtonClick,
                        onHistoryButtonClick = onHistoryButtonClick,
                    )
                }

                if (viewState.balanceLoadState != WalletBalanceLoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TokensSection(
                            viewState = viewState,
                            onEditButtonClick = onEditButtonClick,
                            onTokenClick = onTokenClick,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokensSection(
    viewState: WalletViewState,
    onEditButtonClick: () -> Unit,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    EthCard(modifier = modifier) {
        Column {
            TokensHeader(
                onEditButtonClick = onEditButtonClick,
                modifier = Modifier.padding(16.dp),
            )

            BoxWithConstraints {
                val columnCount = when {
                    maxWidth >= THREE_COLUMNS_MIN_WIDTH_DP.dp -> 3
                    maxWidth >= TWO_COLUMNS_MIN_WIDTH_DP.dp -> 2
                    else -> 1
                }
                val itemWidth = maxWidth / columnCount

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = columnCount,
                ) {
                    if (viewState.balanceLoadState == WalletBalanceLoadState.Loading) {
                        repeat(TOKEN_SHIMMER_COUNT) {
                            Box(modifier = Modifier.width(itemWidth)) {
                                TokenItemShimmer()
                            }
                        }
                    } else {
                        viewState.favoriteTokens.forEach { token ->
                            TokenRow(
                                token = token,
                                onTokenClick = onTokenClick,
                                modifier = Modifier.width(itemWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokensHeader(
    onEditButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            EthText(
                text = stringResource(Res.string.wallet_tokens_erc20),
                style = EthTextStyle.Title,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = 20.sp,
                ),
                maxLines = 1,
            )
        }

        EthButton(
            text = stringResource(Res.string.edit),
            onClick = { onEditButtonClick() },
            style = EthButtonStyle.Text,
        )
    }
}

@Composable
private fun BalanceErrorCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EthCard(
        modifier = modifier.padding(16.dp),
        tone = EthCardTone.Error,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EthText(
                text = stringResource(Res.string.wallet_balance_error_title),
                style = EthTextStyle.Title,
            )
            Spacer(modifier = Modifier.height(8.dp))
            EthText(text = stringResource(Res.string.wallet_balance_error_message))
            Spacer(modifier = Modifier.height(16.dp))
            EthButton(text = stringResource(Res.string.wallet_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun TokenRow(
    token: FavoriteToken,
    onTokenClick: (tokenAddress: EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    TokenItem(
        tokenAddress = token.address,
        tokenName = token.name,
        tokenSymbol = token.ticker,
        tokenBalance = token.amount,
        fiatValue = token.usdValue?.let { token.usdValueFormatted },
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTokenClick(token.address) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ButtonsPanel(
    isLoading: Boolean,
    isSendEnabled: Boolean,
    onReceiveClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    onHistoryButtonClick: () -> Unit,
) {
    val receiveTitle = stringResource(Res.string.wallet_receive)
    val sendTitle = stringResource(Res.string.wallet_send)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularButtonWithTitle(
            title = receiveTitle,
            icon = painterResource(Res.drawable.arrow_downward_24px),
            onClick = onReceiveClick,
        )

        Spacer(modifier = Modifier.width(32.dp))

        if (isLoading) {
            CircularButtonWithTitleShimmer(title = sendTitle)
        } else {
            CircularButtonWithTitle(
                title = sendTitle,
                icon = painterResource(Res.drawable.arrow_outward_24px),
                onClick = onSendButtonClick,
                enabled = isSendEnabled,
            )
        }
        Spacer(modifier = Modifier.width(32.dp))
        CircularButtonWithTitle(
            title = stringResource(Res.string.history_title),
            icon = painterResource(Res.drawable.history_24px),
            onClick = onHistoryButtonClick,
        )
    }
}

@Composable
private fun CircularButtonWithTitle(
    title: String,
    icon: Painter,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EthIconButton(
            onClick = onClick,
            enabled = enabled,
            filled = true,
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        EthText(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.alpha(if (enabled) 1f else 0.6f),
        )
    }
}

@Composable
private fun CircularButtonWithTitleShimmer(title: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmer(),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .shimmer(),
        ) {
            EthText(
                text = title,
                modifier = Modifier.alpha(0f),
            )
        }
    }
}

@Composable
private fun TotalFiatBalanceCard(
    fiatBalance: String?,
    ethBalance: String,
    chainId: Long,
    balanceLoadState: WalletBalanceLoadState,
    fiatLoadState: WalletFiatLoadState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (fiatLoadState) {
            WalletFiatLoadState.Loading -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer(),
                ) {
                    EthText(
                        text = "\$00,000.00",
                        modifier = Modifier.alpha(0f),
                        style = EthTextStyle.Headline,
                        color = MaterialTheme.colorScheme.onBackground,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 12.sp,
                            maxFontSize = 48.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            WalletFiatLoadState.Available -> {
                EthText(
                    text = fiatBalance.orEmpty(),
                    style = EthTextStyle.Headline,
                    color = MaterialTheme.colorScheme.onBackground,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = 48.sp,
                    ),
                    maxLines = 1,
                )
            }
            WalletFiatLoadState.Unavailable -> {
                EthText(
                    text = stringResource(Res.string.wallet_fiat_unavailable),
                    style = EthTextStyle.Title,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier.then(
                if (balanceLoadState == WalletBalanceLoadState.Loading) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                } else {
                    Modifier
                },
            ),
        ) {
            val displayedEthBalance =
                if (balanceLoadState == WalletBalanceLoadState.Loading) "0.000 ETH" else ethBalance
            EthText(
                text = displayedEthBalance,
                style = EthTextStyle.Title,
                modifier = Modifier
                    .alpha(if (balanceLoadState == WalletBalanceLoadState.Loading) 0f else 1f)
                    .clearAndSetSemantics {
                        if (balanceLoadState != WalletBalanceLoadState.Loading) {
                            contentDescription = "ETH balance: $displayedEthBalance"
                        }
                    },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        EthText(
            text = stringResource(Res.string.wallet_network, chainId.toNetworkName()),
            style = EthTextStyle.Caption,
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
private fun TokenItem(
    tokenAddress: EthAddress,
    tokenName: String,
    tokenSymbol: String,
    tokenBalance: String,
    fiatValue: String?,
    modifier: Modifier = Modifier,
) {
    val tokenIcon: DrawableResource = remember(tokenAddress) {
        findKnownToken(tokenAddress)?.iconRes ?: Res.drawable.ic_question_mark_56
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
            EthText(
                text = tokenName,
                style = EthTextStyle.Title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EthText(
                text = tokenSymbol,
                style = EthTextStyle.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            EthText(
                text = "$tokenBalance $tokenSymbol",
                style = EthTextStyle.Title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EthText(
                text = fiatValue ?: stringResource(Res.string.wallet_fiat_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TokensCardShimmer() {
    EthCard {
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
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
}

private class WalletViewStateProvider : PreviewParameterProvider<WalletViewState> {
    override val values: Sequence<WalletViewState> = sequenceOf(
        WalletViewState(
            chainId = 11155111L,
            walletName = "Daily spending",
            totalBalanceUsd = "$12,345.67",
            totalBalanceEth = "5.234 ETH",
            favoriteTokens = listOf(
                FavoriteToken(
                    address = usdtOnSepolia.ethAddress,
                    name = "Tether USD",
                    ticker = "USDT",
                    amount = "1,250.00",
                    usdValue = java.math.BigDecimal("1250.00"),
                    usdValueFormatted = "$1,250.00",
                ),
                FavoriteToken(
                    address = usdcOnSepolia.ethAddress,
                    name = "USD Coin",
                    ticker = "USDC",
                    amount = "500.00",
                    usdValue = java.math.BigDecimal("500.00"),
                    usdValueFormatted = "$500.00",
                ),
                FavoriteToken(
                    address = daiOnSepolia.ethAddress,
                    name = "Dai Stablecoin",
                    ticker = "DAI",
                    amount = "750.50",
                    usdValue = java.math.BigDecimal("750.50"),
                    usdValueFormatted = "$750.50",
                ),
            ),
            balanceLoadState = WalletBalanceLoadState.Content,
            fiatLoadState = WalletFiatLoadState.Available,
        ),
        WalletViewState(
            walletName = "Daily spending",
            balanceLoadState = WalletBalanceLoadState.Loading,
            fiatLoadState = WalletFiatLoadState.Loading,
        ),
        WalletViewState(
            walletName = "Daily spending",
            balanceLoadState = WalletBalanceLoadState.Error,
            fiatLoadState = WalletFiatLoadState.Unavailable,
        ),
    )
}

@FormFactorPreviews
@Composable
private fun WalletScreenContentPreview(
    @PreviewParameter(WalletViewStateProvider::class) viewState: WalletViewState,
) {
    MaterialTheme {
        WalletScreenContent(
            viewState = viewState,
            onUpButtonClick = {},
            showUpButton = true,
            onEditButtonClick = {},
            onTokenClick = {},
            onReceiveButtonClick = {},
            onSendButtonClick = {},
            onHistoryButtonClick = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}
