package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.CurrentWalletEntity
import me.sdi.ethwallet.database.entity.WalletEntity

class FakeWalletDao : WalletDao {
    private val walletsState = MutableStateFlow<List<WalletEntity>>(emptyList())
    private val currentWalletState = MutableStateFlow<CurrentWalletEntity?>(null)

    var wallets: List<WalletEntity>
        get() = walletsState.value
        set(value) {
            walletsState.value = value
            if (currentWalletState.value?.address !in value.map { it.address }) {
                currentWalletState.value = null
            }
        }

    var currentWalletAddress: String?
        get() = currentWalletState.value?.address
        set(value) {
            currentWalletState.value = value?.let { CurrentWalletEntity(address = it) }
        }

    var shouldThrowException = false
    var exceptionToThrow: Throwable? = null

    private fun checkErrors() {
        exceptionToThrow?.let { throw it }
        if (shouldThrowException) throw RuntimeException("Simulated database failure")
    }

    override suspend fun insertWalletInternal(wallet: WalletEntity) {
        checkErrors()
        wallets = wallets.filter { it.address != wallet.address } + wallet
    }

    override suspend fun setActiveWallet(currentWalletEntity: CurrentWalletEntity) {
        checkErrors()
        check(wallets.any { it.address == currentWalletEntity.address }) {
            "Wallet not found: ${currentWalletEntity.address}"
        }
        currentWalletState.value = currentWalletEntity
    }

    override suspend fun deleteWalletByAddress(address: String) {
        checkErrors()
        wallets = wallets.filter { it.address != address }
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

    override fun currentActiveWallet(): Flow<WalletEntity?> = flow {
        checkErrors()
        emitAll(
            combine(walletsState, currentWalletState) { wallets, currentWallet ->
                wallets.firstOrNull { it.address == currentWallet?.address }
            },
        )
    }
}
