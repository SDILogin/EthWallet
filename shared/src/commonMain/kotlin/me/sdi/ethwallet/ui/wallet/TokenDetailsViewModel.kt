package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens
import org.jetbrains.compose.resources.DrawableResource
import java.math.BigDecimal

class TokenDetailsViewModel(
    walletAddress: EthAddress,
    tokenAddress: EthAddress,
    balanceProvider: BalanceProvider,
    chainConfig: ChainConfig,
) : ViewModel() {
    private val token = supportedKnownTokens(chainConfig.chainId).first { it.ethAddress == tokenAddress }
    private val baseState = token.toDetailsViewState(chainConfig.chainId)

    val viewState: StateFlow<TokenDetailsViewState> = balanceProvider.observe(walletAddress)
        .map { balances ->
            baseState.copy(
                balance = balances[tokenAddress]?.let {
                    BigDecimal(it, token.decimals).stripTrailingZeros().toPlainString()
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), baseState)
}

data class TokenDetailsViewState(
    val name: String,
    val symbol: String,
    val address: String,
    val decimals: Int,
    val icon: DrawableResource,
    val chainId: Long,
    val balance: String? = null,
)

internal fun KnownToken.toDetailsViewState(chainId: Long) = TokenDetailsViewState(
    name = tokenName,
    symbol = symbol,
    address = ethAddress.toString(),
    decimals = decimals,
    icon = iconRes,
    chainId = chainId,
)
