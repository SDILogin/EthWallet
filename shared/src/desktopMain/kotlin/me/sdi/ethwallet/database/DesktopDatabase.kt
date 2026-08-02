package me.sdi.ethwallet.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.CurrentWalletEntity
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.database.entity.WalletEntity

class AppDatabase private constructor() {
    fun walletDao(): WalletDao = DesktopWalletDao()

    fun tokenBalanceDao(): TokenBalanceDao = DesktopTokenBalanceDao()

    fun fiatRateDao(): FiatRateDao = DesktopFiatRateDao()

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(): AppDatabase {
            return instance ?: synchronized(this) {
                AppDatabase().also { instance = it }
            }
        }
    }
}

object DesktopDatabaseBuilder {
    fun getInstance(): AppDatabase = AppDatabase.getInstance()
}

private class DesktopWalletDao : WalletDao {
    override suspend fun insertWalletInternal(wallet: WalletEntity) {}

    override suspend fun setActiveWallet(currentWalletEntity: CurrentWalletEntity) {}

    override fun observeAllWallets(): Flow<List<WalletEntity>> = flowOf(emptyList())

    override suspend fun getEncryptedPrivateKeyByAddress(address: String): String = ""

    override fun currentActiveWallet(): Flow<WalletEntity?> = flowOf(null)

    override suspend fun deleteWalletByAddress(address: String) {}
}

private class DesktopTokenBalanceDao : TokenBalanceDao {
    override suspend fun read(walletAddress: String, tokenAddress: String): TokenBalanceEntity? =
        null

    override fun observe(walletAddress: String): Flow<List<TokenBalanceEntity>> = flowOf(emptyList())

    override suspend fun insertAll(tokenBalances: List<TokenBalanceEntity>) {}
}

private class DesktopFiatRateDao : FiatRateDao {
    override suspend fun readAll(tokenAddresses: List<String>): List<FiatRateEntity> = emptyList()

    override fun observeAll(tokenAddresses: List<String>): Flow<List<FiatRateEntity>> =
        flowOf(emptyList())

    override suspend fun insertAll(fiatRates: List<FiatRateEntity>) {}
}
