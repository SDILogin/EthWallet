package me.sdi.ethwallet.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
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
    private val wallets = MutableStateFlow<List<WalletEntity>>(emptyList())

    override suspend fun insertWallet(wallet: WalletEntity) {
        wallets.value = wallets.value.filter { it.address != wallet.address } + wallet
    }

    override suspend fun updateWalletLabel(address: String, label: String) {
        wallets.value = wallets.value.map { wallet ->
            if (wallet.address == address) wallet.copy(label = label) else wallet
        }
    }

    override fun observeAllWallets(): Flow<List<WalletEntity>> = wallets

    override suspend fun getEncryptedPrivateKeyByAddress(address: String): String =
        wallets.value.firstOrNull { it.address == address }?.encryptedPrivateKeyBase64
            ?: throw IllegalStateException("Wallet not found: $address")

    override suspend fun deleteWalletByAddress(address: String) {
        wallets.value = wallets.value.filter { it.address != address }
    }
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
