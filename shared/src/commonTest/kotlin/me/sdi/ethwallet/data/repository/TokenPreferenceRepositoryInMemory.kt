package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.core.EthAddress

class TokenPreferenceRepositoryInMemory : TokenPreferenceRepository {
    private val preferences = MutableStateFlow(emptyMap<EthAddress, List<TokenPreference>>())

    override fun observe(walletAddress: EthAddress): Flow<List<TokenPreference>> =
        preferences.map { it[walletAddress].orEmpty() }

    override suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>) {
        this.preferences.update { it + (walletAddress to preferences) }
    }
}
