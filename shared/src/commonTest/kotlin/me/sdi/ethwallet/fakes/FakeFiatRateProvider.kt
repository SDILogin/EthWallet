package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FiatRateProvider
import java.math.BigDecimal

class FakeFiatRateProvider : FiatRateProvider {
    val rates = MutableStateFlow<Map<EthAddress, BigDecimal>>(emptyMap())

    override suspend fun forceUpdate() = Unit

    override fun observe(): Flow<Map<EthAddress, BigDecimal>> = rates
}
