package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

class FakeWalletDao : WalletDao {
    var wallets: List<WalletEntity> = emptyList()
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

    override suspend fun getAllWallets(): List<WalletEntity> {
        checkErrors()
        return wallets
    }

    override suspend fun clearActiveWallets() {
        checkErrors()
        wallets = wallets.map { it.copy(isActive = false) }
    }

    override suspend fun setWalletActive(address: String) {
        checkErrors()
        wallets = wallets.map {
            if (it.address == address) it.copy(isActive = true) else it
        }
    }
}
