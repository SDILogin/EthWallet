package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
import me.sdi.ethwallet.core.history.logIndex
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailsViewModelTest {
    @Test
    fun selectsLogWithinWalletAndObservesDatabaseChanges() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val wallet = EthAddress("0x${"1".repeat(40)}")
            val hash = "0x${"a".repeat(64)}"
            val receiver = EthAddress("0x${"b".repeat(40)}")
            val selected = Transfer.Erc20(hash, 1L, wallet, receiver, 2, wallet, "USDC", 6, "250000000")
            val walletDao = FakeWalletDao().apply {
                wallets = listOf(
                    WalletEntity(wallet.toString(), "unused", "Main"),
                    WalletEntity("0x" + "B".repeat(40), "unused", "Savings"),
                )
            }
            val walletRepository = WalletRepository(
                walletDao,
                FakeEthManager(),
                FakePrivateKeyEncryptor(),
                HistoryInitializationStoreInMemory(),
            )
            val transfers = MutableStateFlow<List<Transfer>>(listOf(selected.copy(logIndex = 1), selected))
            val repository = object : TransferHistoryRepository {
                override fun observe(walletAddress: EthAddress) = transfers.also { assertEquals(wallet, walletAddress) }

                override fun observe(walletAddress: EthAddress, trxHash: String, logIndex: Int) =
                    transfers.mapNotNull { items ->
                        assertEquals(wallet, walletAddress)
                        assertEquals(hash, trxHash)
                        assertEquals(2, logIndex)
                        items.firstOrNull { it.hash == trxHash && it.logIndex == logIndex }
                    }

                override suspend fun read(walletAddress: EthAddress) = transfers.value

                override suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>) = Unit
            }
            val viewModel = TransactionDetailsViewModel(wallet, hash, 2, repository, walletRepository)
            store.put("details", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
            runCurrent()
            assertEquals(TransactionDetailsState.Content(selected, "Main", "Savings"), viewModel.viewState.value)
            val updated = selected.copy(value = "300000000")
            transfers.value = listOf(updated)
            runCurrent()
            assertEquals(TransactionDetailsState.Content(updated, "Main", "Savings"), viewModel.viewState.value)
            walletDao.wallets = listOf(WalletEntity(wallet.toString(), "unused", "Renamed"))
            val refreshed = updated.copy(value = "400000000")
            transfers.value = listOf(refreshed)
            runCurrent()
            assertEquals(TransactionDetailsState.Content(refreshed, "Renamed", null), viewModel.viewState.value)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
