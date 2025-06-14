package me.sdi.ethwallet.database.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.database.entity.FiatRateEntity

class InMemoryFiatRateDao : FiatRateDao {
    private val rates = mutableListOf<FiatRateEntity>()
    private val _ratesFlow = MutableStateFlow(emptyList<FiatRateEntity>())

    override suspend fun readAll(tokenAddresses: List<String>): List<FiatRateEntity> {
        return rates.filter { it.tokenAddress in tokenAddresses }
    }

    override fun observeAll(tokenAddresses: List<String>): Flow<List<FiatRateEntity>> {
        return _ratesFlow.map { allRates ->
            allRates.filter { it.tokenAddress in tokenAddresses }
        }
    }

    override suspend fun insertAll(fiatRates: List<FiatRateEntity>) {
        fiatRates.forEach { fiatRate ->
            val existingIndex = rates.indexOfFirst { it.tokenAddress == fiatRate.tokenAddress }
            
            if (existingIndex != -1) {
                rates[existingIndex] = fiatRate
            } else {
                rates.add(fiatRate)
            }
        }
        _ratesFlow.value = rates.toList()
    }

    fun clear() {
        rates.clear()
        _ratesFlow.value = emptyList()
    }
}