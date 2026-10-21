package me.sdi.ethwallet.core.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryUpdateInteractorTest {
    private val wallet = EthAddress("0x${"1".repeat(40)}")
    private val receiver = EthAddress("0x${"2".repeat(40)}")
    private val chainId = 1L
    private val initialization = HistoryInitializationStoreInMemory()

    @Test
    fun empty_database_loads_one_page_of_both_sources_and_keeps_cursors() = runTest {
        val provider = FakeTransfersHistoryProvider(listOf(page(native(20), next = 1)), listOf(page(token(19), next = 1)))
        val repository = Repository()
        val result = interactor(provider, repository).refresh(chainId, wallet)

        assertEquals(1, provider.requests.size)
        assertEquals(listOf(native(20), token(19)), repository.writes.single())
        assertEquals(next(1), assertNotNull(result.nextCursor).eth)
        assertEquals(next(1), result.nextCursor.erc20)
    }

    @Test
    fun missing_source_uses_first_page_rule_even_with_pending_cached_native() = runTest {
        val repository = Repository(listOf(native(5, TransactionStatus.Pending)))
        val provider = FakeTransfersHistoryProvider(listOf(page(native(20), next = 1)), listOf(page(token(19), next = 1)))
        interactor(provider, repository).refresh(chainId, wallet)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun refresh_without_pending_stops_each_source_at_cached_identity() = runTest {
        val repository = Repository(listOf(native(10), token(9)))
        val provider = FakeTransfersHistoryProvider(
            listOf(page(native(20), native(10), next = 1)),
            listOf(page(token(19), next = 1), page(token(9), next = 2)),
        )
        val result = interactor(provider, repository).refresh(chainId, wallet)

        assertEquals(2, provider.requests.size)
        assertEquals(TransfersPageRequest.Skip, provider.requests[1].first)
        assertEquals(next(1), provider.requests[1].second)
        assertEquals(1, repository.writes.size)
        assertEquals(listOf(native(20), native(10), token(19), token(9)), repository.writes.single())
        // Older-page pagination starts after the first pages, even when refresh fetched further.
        assertEquals(next(1), assertNotNull(result.nextCursor).erc20)
    }

    @Test
    fun refresh_covers_oldest_pending_timestamp_including_equal_timestamp_pages() = runTest {
        val pending = native(5, TransactionStatus.Pending)
        val repository = Repository(listOf(native(10), token(10), pending))
        val anotherAtBoundary = native(5).copy(hash = hash(55))
        val provider = FakeTransfersHistoryProvider(
            listOf(
                page(native(20), native(10), next = 1),
                page(native(7), native(5), next = 2),
                page(anotherAtBoundary, native(4), next = 3),
            ),
            listOf(page(token(20), next = 1), page(token(5), token(3), next = 2)),
        )
        interactor(provider, repository).refresh(chainId, wallet)

        assertEquals(3, provider.requests.size)
        assertEquals(TransfersPageRequest.Skip, provider.requests[2].second)
        assertTrue(anotherAtBoundary in repository.writes.single())
        assertEquals(TransactionStatus.Confirmed, repository.items.single { it.identity == pending.identity }.status)
    }

    @Test
    fun filtered_page_uses_response_timestamp_range_to_finish_refresh() = runTest {
        val repository = Repository(listOf(native(5, TransactionStatus.Pending), token(10)))
        val filtered = TransfersPage(emptyList(), next(2), oldestTimestamp = 4)
        val provider = FakeTransfersHistoryProvider(listOf(filtered), listOf(filtered))
        interactor(provider, repository).refresh(chainId, wallet)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun exhausted_source_finishes_without_overlap_and_keeps_missing_pending_status() = runTest {
        val pending = native(5, TransactionStatus.Pending)
        val repository = Repository(listOf(pending, token(10)))
        val provider = FakeTransfersHistoryProvider(listOf(page(native(20))), listOf(page(token(20))))
        assertNull(interactor(provider, repository).refresh(chainId, wallet).nextCursor)
        assertEquals(TransactionStatus.Pending, repository.items.single { it.identity == pending.identity }.status)
    }

    @Test
    fun failure_on_later_refresh_page_leaves_database_and_initialization_unchanged() = runTest {
        val original = listOf(native(10), token(10))
        val repository = Repository(original)
        val provider = FakeTransfersHistoryProvider(listOf(page(native(20), next = 1)), listOf(page(token(20))))
        provider.failure = { eth, _ -> if (eth == next(1)) error("Network failure") }
        assertFailsWith<IllegalStateException> { interactor(provider, repository).refresh(chainId, wallet) }
        assertEquals(emptyList(), repository.writes)
        assertEquals(original, repository.items)
    }

    @Test
    fun append_skips_exhausted_source_and_failed_append_retries_same_cursor() = runTest {
        val repository = Repository()
        val provider = FakeTransfersHistoryProvider(listOf(page(native(20), next = 1), page(native(10))), listOf(page(token(19))))
        val updater = interactor(provider, repository)
        val cursor = assertNotNull(updater.refresh(chainId, wallet).nextCursor)
        provider.failure = { eth, _ -> if (eth == next(1)) error("Network failure") }
        assertFailsWith<IllegalStateException> { updater.loadMore(chainId, wallet, cursor) }
        assertEquals(1, repository.writes.size)

        provider.failure = { _, _ -> }
        assertNull(updater.loadMore(chainId, wallet, cursor).nextCursor)
        assertEquals(listOf(native(10)), repository.writes.last())
        assertEquals(provider.requests[1], provider.requests[2])
        assertEquals(TransfersPageRequest.Skip, provider.requests[2].second)
    }

    @Test
    fun empty_page_with_next_cursor_remains_pageable() = runTest {
        val repository = Repository()
        val provider = FakeTransfersHistoryProvider(
            listOf(page(next = 1), page(next = 2), page(native(3))),
            listOf(page(token(10))),
        )
        val updater = interactor(provider, repository)
        val first = assertNotNull(updater.refresh(chainId, wallet).nextCursor)
        val second = assertNotNull(updater.loadMore(chainId, wallet, first).nextCursor)
        assertNull(updater.loadMore(chainId, wallet, second).nextCursor)
        assertEquals(listOf(native(3)), repository.writes.last())
    }

    @Test
    fun pending_to_failed_is_saved_but_not_observed_as_visible_history() = runTest {
        val repository = Repository(listOf(native(5, TransactionStatus.Pending), token(5)))
        val provider = FakeTransfersHistoryProvider(listOf(page(native(5, TransactionStatus.Failed))), listOf(page(token(5))))
        interactor(provider, repository).refresh(chainId, wallet)
        assertEquals(TransactionStatus.Failed, repository.items.single { it is Transfer.Eth }.status)
        assertEquals(listOf(token(5)), repository.visible())
    }

    @Test
    fun cursor_is_bound_to_wallet_and_chain_and_cancellation_is_rethrown() = runTest {
        val repository = Repository()
        val provider = FakeTransfersHistoryProvider(listOf(page(next = 1)), listOf(page()))
        val updater = interactor(provider, repository)
        val cursor = assertNotNull(updater.refresh(chainId, wallet).nextCursor)
        assertFailsWith<IllegalArgumentException> { updater.loadMore(2, wallet, cursor) }
        assertFailsWith<IllegalArgumentException> { updater.loadMore(chainId, receiver, cursor) }
        assertEquals(1, provider.requests.size)
        provider.failure = { _, _ -> throw CancellationException("Cancelled") }
        assertFailsWith<CancellationException> { updater.loadMore(chainId, wallet, cursor) }
        assertEquals(1, repository.writes.size)
    }

    private fun interactor(provider: TransfersHistoryProvider, repository: TransferHistoryRepository) =
        HistoryUpdateInteractorDefault(provider, repository, initialization)

    private fun hash(value: Int) = "0x${value.toString(16).padStart(64, '0')}"

    private fun native(time: Long, status: TransactionStatus = TransactionStatus.Confirmed) = Transfer.Eth(
        hash = hash(time.toInt()),
        timestamp = time,
        from = wallet,
        to = receiver,
        valueWei = "1".toBigInteger().toWei(),
        status = status,
    )

    private fun token(time: Long) = Transfer.Erc20(
        hash = hash(time.toInt()),
        timestamp = time,
        from = wallet,
        to = receiver,
        logIndex = 0,
        tokenAddress = receiver,
        tokenSymbol = "TOKEN",
        tokenDecimals = 6,
        value = "1",
    )

    private fun next(index: Int) = TransfersPageRequest.Next(mapOf("index" to index.toString()))

    private fun page(vararg items: Transfer, next: Int? = null) = TransfersPage(
        items.toList(),
        next?.let(::next) ?: TransfersPageRequest.Skip,
        items.minOfOrNull { it.timestamp },
    )

    private class FakeTransfersHistoryProvider(
        private val ethPages: List<TransfersPage>,
        private val tokenPages: List<TransfersPage>,
    ) : TransfersHistoryProvider {
        val requests = mutableListOf<Pair<TransfersPageRequest, TransfersPageRequest>>()
        var failure: (TransfersPageRequest, TransfersPageRequest) -> Unit = { _, _ -> }

        override suspend fun getTransfers(
            address: EthAddress,
            eth: TransfersPageRequest,
            erc20: TransfersPageRequest,
        ): CombinedTransfersPage {
            requests += eth to erc20
            failure(eth, erc20)

            fun select(pages: List<TransfersPage>, request: TransfersPageRequest): TransfersPage? =
                when (request) {
                    TransfersPageRequest.Skip -> null
                    TransfersPageRequest.First -> pages[0]
                    is TransfersPageRequest.Next -> pages[request.parameters.getValue("index").toInt()]
                }
            return CombinedTransfersPage(select(ethPages, eth), select(tokenPages, erc20))
        }
    }

    private class Repository(initial: List<Transfer> = emptyList()) : TransferHistoryRepository {
        var items = initial
        val writes = mutableListOf<List<Transfer>>()

        fun visible() = items.filter { it.status != TransactionStatus.Failed }

        override fun observe(walletAddress: EthAddress): Flow<List<Transfer>> = flowOf(visible())

        override suspend fun read(walletAddress: EthAddress) = items

        override suspend fun upsert(walletAddress: EthAddress, items: List<Transfer>) {
            writes += items
            val updated = items.mapTo(mutableSetOf()) { it.identity }
            this.items = this.items.filterNot { it.identity in updated } + items.distinctBy { it.identity }
        }
    }
}
