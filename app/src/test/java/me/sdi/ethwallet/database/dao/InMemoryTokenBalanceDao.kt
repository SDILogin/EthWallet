package me.sdi.ethwallet.database.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.database.entity.TokenBalanceEntity

class InMemoryTokenBalanceDao : TokenBalanceDao {
    private val balances = mutableListOf<TokenBalanceEntity>()
    private val _balancesFlow = MutableStateFlow(emptyList<TokenBalanceEntity>())

    override suspend fun read(walletAddress: String, tokenAddress: String): TokenBalanceEntity? {
        return balances.find {
            it.walletAddress == walletAddress && it.tokenAddress == tokenAddress
        }
    }

    override fun observe(walletAddress: String): Flow<List<TokenBalanceEntity>> {
        return _balancesFlow.map { allBalances ->
            allBalances.filter { it.walletAddress == walletAddress }
        }
    }

    override suspend fun insertAll(tokenBalances: List<TokenBalanceEntity>) {
        tokenBalances.forEach { tokenBalance ->
            val existingIndex = balances.indexOfFirst {
                it.walletAddress == tokenBalance.walletAddress &&
                    it.tokenAddress == tokenBalance.tokenAddress
            }

            if (existingIndex != -1) {
                balances[existingIndex] = tokenBalance
            } else {
                balances.add(tokenBalance)
            }
        }
        _balancesFlow.value = balances.toList()
    }

    fun clear() {
        balances.clear()
        _balancesFlow.value = emptyList()
    }
}
