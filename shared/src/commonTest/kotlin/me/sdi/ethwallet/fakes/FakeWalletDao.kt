package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

class FakeWalletDao : WalletDao {
    private val walletsState = MutableStateFlow<List<WalletEntity>>(emptyList())

    var wallets: List<WalletEntity>
        get() = walletsState.value
        set(value) {
            walletsState.value = value
        }

    var shouldThrowException = false
    var exceptionToThrow: Throwable? = null

    private fun checkErrors() {
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
    }

    override suspend fun insertWallet(wallet: WalletEntity) {
        checkErrors()
        wallets = wallets.filter { it.address != wallet.address } + wallet
    }

    override suspend fun deleteWalletByAddress(address: String) {
        checkErrors()
        wallets = wallets.filter { it.address != address }
    }

    override suspend fun updateWalletLabel(address: String, label: String) {
        checkErrors()
        wallets = wallets.map { wallet ->
            if (wallet.address == address) wallet.copy(label = label) else wallet
        }
    }

    override fun observeAllWallets(): Flow<List<WalletEntity>> = flow {
        checkErrors()
        emitAll(walletsState)
    }

    override suspend fun getEncryptedPrivateKeyByAddress(address: String): String {
        checkErrors()
        return wallets.firstOrNull { it.address == address }?.encryptedPrivateKeyBase64
            ?: throw IllegalStateException("Wallet not found: $address")
    }
}
