package me.sdi.ethwallet.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.TransferHistoryDao
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import me.sdi.ethwallet.database.entity.TransferHistoryEntity
import me.sdi.ethwallet.database.entity.WalletEntity

class AppDatabase private constructor() {
    fun walletDao(): WalletDao = DesktopWalletDao()

    fun tokenBalanceDao(): TokenBalanceDao = DesktopTokenBalanceDao()

    fun fiatRateDao(): FiatRateDao = DesktopFiatRateDao()

    fun transferHistoryDao(): TransferHistoryDao = DesktopTransferHistoryDao

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
    private val balances = MutableStateFlow<List<TokenBalanceEntity>>(emptyList())

    override suspend fun read(walletAddress: String, tokenAddress: String): TokenBalanceEntity? =
        balances.value.firstOrNull { it.walletAddress == walletAddress && it.tokenAddress == tokenAddress }

    override fun observe(walletAddress: String): Flow<List<TokenBalanceEntity>> =
        balances.map { items -> items.filter { it.walletAddress == walletAddress } }

    override suspend fun insertAll(tokenBalances: List<TokenBalanceEntity>) {
        val updates = tokenBalances.associateBy { it.walletAddress to it.tokenAddress }
        balances.update { existing ->
            existing.filterNot { (it.walletAddress to it.tokenAddress) in updates } + updates.values
        }
    }
}

private class DesktopFiatRateDao : FiatRateDao {
    override suspend fun readAll(tokenAddresses: List<String>): List<FiatRateEntity> = emptyList()

    override fun observeAll(tokenAddresses: List<String>): Flow<List<FiatRateEntity>> =
        flowOf(emptyList())

    override suspend fun insertAll(fiatRates: List<FiatRateEntity>) {}
}

private object DesktopTransferHistoryDao : TransferHistoryDao {
    private val items = MutableStateFlow<List<TransferHistoryEntity>>(emptyList())

    override fun observe(chainId: Long, walletAddress: String): Flow<List<TransferHistoryEntity>> =
        items.map { entities ->
            entities
                .filter { it.chainId == chainId && it.walletAddress == walletAddress && it.status != "Failed" }
                .sortedWith(
                    compareByDescending<TransferHistoryEntity> { it.status == "Pending" }
                        .thenByDescending { it.timestamp }
                        .thenBy { it.transactionHash }
                        .thenBy { it.logIndex },
                )
        }

    override suspend fun readWalletTransfers(chainId: Long, walletAddress: String): List<TransferHistoryEntity> =
        items.value.filter { it.chainId == chainId && it.walletAddress == walletAddress }

    override suspend fun upsertAll(items: List<TransferHistoryEntity>) {
        val updatedKeys = items.mapTo(mutableSetOf(), TransferHistoryEntity::key)
        this.items.update { existing ->
            existing.filterNot { it.key() in updatedKeys } + items.associateBy(TransferHistoryEntity::key).values
        }
    }
}

private fun TransferHistoryEntity.key() = listOf(chainId, walletAddress, transactionHash, logIndex)
