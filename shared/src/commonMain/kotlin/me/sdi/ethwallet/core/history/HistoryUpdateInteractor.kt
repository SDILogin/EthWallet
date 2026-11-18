package me.sdi.ethwallet.core.history

import me.sdi.ethwallet.core.EthAddress

interface HistoryUpdateInteractor {
    /** Saves new transfers and deletes replaced token placeholders. Then reads the status of unlisted pending transfers. */
    suspend fun refresh(chainId: Long, walletAddress: EthAddress): HistoryLoadResult

    /** Loads one page from each active source. The cursor advances only after the DB batch succeeds. */
    suspend fun loadMore(chainId: Long, walletAddress: EthAddress, cursor: HistoryPaginationCursor): HistoryLoadResult
}

/** A null [nextCursor] means both sources are exhausted. */
data class HistoryLoadResult(val nextCursor: HistoryPaginationCursor?)

data class HistoryPaginationCursor(
    val chainId: Long,
    val walletAddress: EthAddress,
    val eth: TransfersPageRequest,
    val erc20: TransfersPageRequest,
)

class HistoryUpdateInteractorDefault(
    private val historyProvider: TransfersHistoryProvider,
    private val historyRepository: TransferHistoryRepository,
    private val initializationStore: HistoryInitializationStore,
) : HistoryUpdateInteractor {
    override suspend fun refresh(chainId: Long, walletAddress: EthAddress): HistoryLoadResult {
        val cachedTransfers = historyRepository.read(walletAddress)
        val noEthTransfers = cachedTransfers.none { it is Transfer.Eth }
        val noErc20Transfers = cachedTransfers.none { it is Transfer.Erc20 }
        val loadOnlyFirstPage = noEthTransfers || noErc20Transfers

        val firstPage = historyProvider.getTransfers(
            address = walletAddress,
            eth = TransfersPageRequest.First,
            erc20 = TransfersPageRequest.First,
        )
        val refreshedTransfers = firstPage.items.toMutableList()

        if (!loadOnlyFirstPage) {
            val oldestPendingTimestamp =
                cachedTransfers.filter { it.status == TransactionStatus.Pending }.minOfOrNull { it.timestamp }
            val cachedEthIdentities =
                cachedTransfers.filterIsInstance<Transfer.Eth>().mapTo(mutableSetOf()) { it.identity }
            val cachedErc20Identities =
                cachedTransfers.filterIsInstance<Transfer.Erc20>().mapTo(mutableSetOf()) { it.identity }

            var page = firstPage
            while (true) {
                val ethRefreshRequest = page.eth?.nextRequestForRefresh(
                    oldestPendingTimestamp = oldestPendingTimestamp,
                    cachedTransferIdentities = cachedEthIdentities,
                ) ?: TransfersPageRequest.Skip
                val erc20RefreshRequest = page.erc20?.nextRequestForRefresh(
                    oldestPendingTimestamp = oldestPendingTimestamp,
                    cachedTransferIdentities = cachedErc20Identities,
                ) ?: TransfersPageRequest.Skip
                if (ethRefreshRequest == TransfersPageRequest.Skip && erc20RefreshRequest == TransfersPageRequest.Skip) {
                    break
                }

                page = historyProvider.getTransfers(
                    address = walletAddress,
                    eth = ethRefreshRequest,
                    erc20 = erc20RefreshRequest,
                )
                refreshedTransfers += page.items
            }
        }

        historyRepository.upsert(walletAddress, refreshedTransfers)
        deleteReplacedPlaceholders(walletAddress, cachedTransfers, refreshedTransfers)
        initializationStore.markInitialized(chainId, walletAddress)
        updateUnlistedPendingStatuses(cachedTransfers, refreshedTransfers)

        val olderHistoryCursor = firstPage.toHistoryPaginationCursor(chainId, walletAddress)
        return HistoryLoadResult(olderHistoryCursor)
    }

    override suspend fun loadMore(chainId: Long, walletAddress: EthAddress, cursor: HistoryPaginationCursor): HistoryLoadResult {
        require(cursor.chainId == chainId && cursor.walletAddress == walletAddress) { "History cursor belongs to another wallet or chain" }
        val page = historyProvider.getTransfers(
            address = walletAddress,
            eth = cursor.eth,
            erc20 = cursor.erc20,
        )
        historyRepository.upsert(walletAddress, page.items)
        return HistoryLoadResult(page.toHistoryPaginationCursor(chainId, walletAddress))
    }

    /**
     * A sent transfer is saved as Pending before the API lists it. Some sent transfers are never listed: a reverted
     * token transfer has no log. Their status is read by hash.
     */
    private suspend fun updateUnlistedPendingStatuses(cachedTransfers: List<Transfer>, fetchedTransfers: List<Transfer>) {
        val fetchedHashes = fetchedTransfers.mapTo(mutableSetOf()) { it.hash.lowercase() }
        val unlistedHashes = cachedTransfers
            .filter { it.status == TransactionStatus.Pending && it.hash.lowercase() !in fetchedHashes }
            .map { it.hash }
            .distinctBy { it.lowercase() }
        for (hash in unlistedHashes) {
            val summary = historyProvider.getTransaction(hash)
            if (summary != null && summary.status != TransactionStatus.Pending) {
                historyRepository.update(hash, summary.status, summary.fee)
            }
        }
    }

    /** A sent token transfer is saved without its log index. The fetched transfer with the real index replaces it. */
    private suspend fun deleteReplacedPlaceholders(
        walletAddress: EthAddress,
        cachedTransfers: List<Transfer>,
        fetchedTransfers: List<Transfer>,
    ) {
        val fetchedTokenHashes = fetchedTransfers.filterIsInstance<Transfer.Erc20>().mapTo(mutableSetOf()) { it.hash.lowercase() }
        val replaced = cachedTransfers.filter {
            it is Transfer.Erc20 && it.logIndex == UNKNOWN_LOG_INDEX && it.hash.lowercase() in fetchedTokenHashes
        }
        if (replaced.isNotEmpty()) {
            historyRepository.delete(walletAddress, replaced)
        }
    }

    private fun TransfersPage.nextRequestForRefresh(
        oldestPendingTimestamp: Long?,
        cachedTransferIdentities: Set<Pair<String, Int>>,
    ): TransfersPageRequest {
        if (nextRequest == TransfersPageRequest.Skip) return TransfersPageRequest.Skip

        if (oldestPendingTimestamp != null) {
            // Continue through equal timestamps so a boundary split over several pages is fully covered.
            val pendingRangeCovered = oldestTimestamp != null && oldestTimestamp < oldestPendingTimestamp
            return if (pendingRangeCovered) TransfersPageRequest.Skip else nextRequest
        }

        val reachedCachedTransfer = items.any { it.identity in cachedTransferIdentities }
        return if (reachedCachedTransfer) TransfersPageRequest.Skip else nextRequest
    }

    private fun CombinedTransfersPage.toHistoryPaginationCursor(
        chainId: Long,
        walletAddress: EthAddress,
    ): HistoryPaginationCursor? {
        val nextEthRequest = eth?.nextRequest ?: TransfersPageRequest.Skip
        val nextErc20Request = erc20?.nextRequest ?: TransfersPageRequest.Skip
        if (nextEthRequest == TransfersPageRequest.Skip && nextErc20Request == TransfersPageRequest.Skip) {
            return null
        }
        return HistoryPaginationCursor(
            chainId = chainId,
            walletAddress = walletAddress,
            eth = nextEthRequest,
            erc20 = nextErc20Request,
        )
    }
}
