package me.sdi.ethwallet.ui.wallet

import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.ui.KnownToken

internal fun tokenListItems(
    knownTokens: List<KnownToken>,
    preferences: List<TokenPreference>,
): List<TokenListItem> {
    val storedItems = preferences.mapNotNull { preference ->
        knownTokens
            .firstOrNull { it.ethAddress == preference.tokenAddress }
            ?.let { TokenListItem(knownToken = it, showOnMain = preference.visible) }
    }
    val newItems = knownTokens
        .filter { token -> preferences.none { it.tokenAddress == token.ethAddress } }
        .map { TokenListItem(knownToken = it, showOnMain = true) }
    return storedItems + newItems
}
