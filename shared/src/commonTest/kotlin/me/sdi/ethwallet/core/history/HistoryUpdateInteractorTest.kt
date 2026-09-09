package me.sdi.ethwallet.core.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HistoryUpdateInteractorTest {
    private val wallet = EthAddress("0x1111111111111111111111111111111111111111")
    private val receiver = EthAddress("0x2222222222222222222222222222222222222222")
    private val token = EthAddress("0x3333333333333333333333333333333333333333")

    private val chainId = 1L
    private val historyInitializationStore = HistoryInitializationStoreInMemory()

    @Test
    fun update_normalizes_both_feeds_before_single_upsert() = runTest {
        val tokenHash = hash('a')
        val provider = FakeHistoryProvider(
            transactions = listOf(
                transaction(tokenHash, value = "0", status = TransactionStatus.Confirmed),
                transaction(hash('b'), value = "0", status = TransactionStatus.Failed),
                transaction(hash('c'), value = "42", status = TransactionStatus.Confirmed),
            ),
            tokenTransfers = listOf(
                tokenTransfer(tokenHash, logIndex = 7),
                tokenTransfer(hash('d'), logIndex = null),
            ),
        )
        val repository = RecordingHistoryRepository()

        HistoryUpdateInteractorDefault(provider, repository, historyInitializationStore)
            .update(chainId, wallet)

        assertEquals(1, repository.upsertCalls.size)
        val call = repository.upsertCalls.single()
        assertEquals(wallet, call.walletAddress)
        assertEquals(
            listOf(hash('b'), hash('c'), tokenHash),
            call.items.map(HistoryItem::transactionHash),
        )
        assertEquals(HistoryItem.TokenTransfer::class, call.items.last()::class)
    }

    @Test
    fun update_does_not_write_partial_results_when_a_request_fails() = runTest {
        val failure = IllegalStateException("Token history failed")
        val provider = FakeHistoryProvider(
            transactions = listOf(transaction(hash('a'), value = "1", status = TransactionStatus.Confirmed)),
            tokenTransfersFailure = failure,
        )
        val repository = RecordingHistoryRepository()

        val thrown = assertFailsWith<IllegalStateException> {
            HistoryUpdateInteractorDefault(provider, repository, historyInitializationStore)
                .update(chainId, wallet)
        }

        assertEquals(failure.message, thrown.message)
        assertEquals(emptyList(), repository.upsertCalls)
    }

    private fun transaction(hash: String, value: String, status: TransactionStatus) = TransactionHistoryItem(
        hash = hash,
        timestamp = "2026-09-04T08:00:00Z",
        blockNumber = 1,
        from = wallet,
        to = receiver,
        valueWei = value,
        status = status,
    )

    private fun tokenTransfer(hash: String, logIndex: Int?) = TokenTransferHistoryItem(
        transactionHash = hash,
        logIndex = logIndex,
        timestamp = "2026-09-04T08:00:00Z",
        from = wallet,
        to = receiver,
        tokenAddress = token,
        tokenSymbol = "TEST",
        tokenDecimals = 18,
        value = "5",
    )

    private fun hash(character: Char) = "0x${character.toString().repeat(64)}"

    private class FakeHistoryProvider(
        private val transactions: List<TransactionHistoryItem> = emptyList(),
        private val tokenTransfers: List<TokenTransferHistoryItem> = emptyList(),
        private val tokenTransfersFailure: Throwable? = null,
    ) : TransactionHistoryProvider {
        override suspend fun getTransactions(
            address: EthAddress,
            cursor: TransactionHistoryCursor?,
        ) = TransactionHistoryPage(transactions, null)

        override suspend fun getTokenTransfers(
            address: EthAddress,
            cursor: TransactionHistoryCursor?,
        ): TransactionHistoryPage<TokenTransferHistoryItem> {
            tokenTransfersFailure?.let { throw it }
            return TransactionHistoryPage(tokenTransfers, null)
        }
    }

    private class RecordingHistoryRepository : TransactionHistoryRepository {
        val upsertCalls = mutableListOf<UpsertCall>()

        override fun observe(walletAddress: EthAddress): Flow<List<HistoryItem>> = flowOf(emptyList())

        override suspend fun upsert(walletAddress: EthAddress, items: List<HistoryItem>) {
            upsertCalls += UpsertCall(walletAddress, items)
        }
    }

    private data class UpsertCall(
        val walletAddress: EthAddress,
        val items: List<HistoryItem>,
    )
}
