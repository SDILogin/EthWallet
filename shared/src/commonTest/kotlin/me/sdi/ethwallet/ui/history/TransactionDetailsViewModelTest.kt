package me.sdi.ethwallet.ui.history

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransferHistoryRepository
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
            val selected = Transfer.Erc20(hash, 1L, wallet, wallet, 2, wallet, "USDC", 6, "250000000")
            val transfers = MutableStateFlow<List<Transfer>>(listOf(selected.copy(logIndex = 1), selected))
            val repository = object : TransferHistoryRepository {
                override fun observe(walletAddress: EthAddress) = transfers.also { assertEquals(wallet, walletAddress) }

                override suspend fun read(walletAddress: EthAddress) = transfers.value

                override suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>) = Unit
            }
            val viewModel = TransactionDetailsViewModel(wallet, hash.uppercase(), 2, repository)
            store.put("details", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
            runCurrent()
            assertEquals(TransactionDetailsState.Content(selected), viewModel.viewState.value)
            val updated = selected.copy(value = "300000000")
            transfers.value = listOf(updated)
            runCurrent()
            assertEquals(TransactionDetailsState.Content(updated), viewModel.viewState.value)
            transfers.value = emptyList()
            runCurrent()
            assertEquals(TransactionDetailsState.Unavailable, viewModel.viewState.value)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
