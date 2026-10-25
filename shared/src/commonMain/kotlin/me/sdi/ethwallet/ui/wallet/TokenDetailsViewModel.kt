package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.util.formatUsdCurrency
import org.jetbrains.compose.resources.DrawableResource
import java.math.BigDecimal

class TokenDetailsViewModel(
    walletAddress: EthAddress,
    tokenAddress: EthAddress,
    balanceProvider: BalanceProvider,
    chainConfig: ChainConfig,
    fiatRateProvider: FiatRateProvider,
) : ViewModel() {
    private val token = supportedKnownTokens(chainConfig.chainId).first { it.ethAddress == tokenAddress }
    private val baseState = token.toDetailsViewState(chainConfig.chainId)

    val viewState: StateFlow<TokenDetailsViewState> = combine(
        balanceProvider.observe(walletAddress),
        fiatRateProvider.observe().onStart { emit(emptyMap()) },
    ) { balances, rates ->
        val amount = balances[tokenAddress]?.let { BigDecimal(it, token.decimals) }
        baseState.copy(
            balance = amount?.stripTrailingZeros()?.toPlainString(),
            fiatValue = amount?.let { value -> rates[tokenAddress]?.let(value::multiply) }?.let(::formatUsdCurrency),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), baseState)
}

data class TokenDetailsViewState(
    val name: String,
    val symbol: String,
    val address: String,
    val decimals: Int,
    val icon: DrawableResource,
    val chainId: Long,
    val balance: String? = null,
    val fiatValue: String? = null,
)

internal fun KnownToken.toDetailsViewState(chainId: Long) = TokenDetailsViewState(
    name = tokenName,
    symbol = symbol,
    address = ethAddress.toString(),
    decimals = decimals,
    icon = iconRes,
    chainId = chainId,
)
