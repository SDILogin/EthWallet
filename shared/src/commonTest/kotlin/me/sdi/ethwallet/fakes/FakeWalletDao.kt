package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity

class FakeWalletDao : WalletDao {
    var wallets: List<WalletEntity> = emptyList()
    var shouldThrowException = false

    override suspend fun insertWallet(wallet: WalletEntity) {
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
        wallets = wallets.filter { it.address != wallet.address } + wallet
    }

    override suspend fun deleteWalletByAddress(address: String) {
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
        wallets = wallets.filter { it.address != address }
    }

    override suspend fun getAllWallets(): List<WalletEntity> {
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
        return wallets
    }

    override suspend fun clearActiveWallets() {
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
        wallets = wallets.map { it.copy(isActive = false) }
    }

    override suspend fun setWalletActive(address: String) {
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
        wallets = wallets.map {
            if (it.address == address) it.copy(isActive = true) else it
        }
    }
}
