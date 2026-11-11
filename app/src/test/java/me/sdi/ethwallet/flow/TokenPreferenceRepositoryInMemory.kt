package me.sdi.ethwallet.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.data.repository.TokenPreferenceRepository

class TokenPreferenceRepositoryInMemory : TokenPreferenceRepository {
    private val preferences = MutableStateFlow(emptyMap<EthAddress, List<TokenPreference>>())

    override fun observe(walletAddress: EthAddress): Flow<List<TokenPreference>> =
        preferences.map { it[walletAddress].orEmpty() }

    override suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>) {
        this.preferences.update { it + (walletAddress to preferences) }
    }
}
