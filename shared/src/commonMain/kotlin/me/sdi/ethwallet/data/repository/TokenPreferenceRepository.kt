package me.sdi.ethwallet.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.database.dao.TokenPreferenceDao
import me.sdi.ethwallet.database.entity.TokenPreferenceEntity

/**
 * Dashboard display preference of a token. The position of a preference in a list is its display order.
 */
data class TokenPreference(
    val tokenAddress: EthAddress,
    val visible: Boolean,
)

interface TokenPreferenceRepository {
    fun observe(walletAddress: EthAddress): Flow<List<TokenPreference>>

    suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>)
}

class TokenPreferenceRepositoryRoom(
    private val tokenPreferenceDao: TokenPreferenceDao,
    private val chainConfig: ChainConfig,
) : TokenPreferenceRepository {
    override fun observe(walletAddress: EthAddress): Flow<List<TokenPreference>> =
        tokenPreferenceDao
            .observe(chainId = chainConfig.chainId, walletAddress = walletAddress.toString())
            .map { entities ->
                entities.map { TokenPreference(tokenAddress = EthAddress(it.tokenAddress), visible = it.visible) }
            }

    override suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>) {
        tokenPreferenceDao.replace(
            chainId = chainConfig.chainId,
            walletAddress = walletAddress.toString(),
            preferences = preferences.mapIndexed { index, preference ->
                TokenPreferenceEntity(
                    chainId = chainConfig.chainId,
                    walletAddress = walletAddress.toString(),
                    tokenAddress = preference.tokenAddress.toString(),
                    position = index,
                    visible = preference.visible,
                )
            },
        )
    }
}
