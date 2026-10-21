package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryPaginationCursor
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.history.TransfersPageRequest
import me.sdi.ethwallet.core.transfer.toWei
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryReducerTest {
    private val pagination = HistoryPagination.Idle(
        cursor = HistoryPaginationCursor(
            chainId = 1L,
            walletAddress = EthAddress("0x${"1".repeat(40)}"),
            eth = TransfersPageRequest.Next(mapOf("index" to "2")),
            erc20 = TransfersPageRequest.Skip,
        ),
    )
    private val items = listOf(
        Transfer.Eth(
            hash = "0x${"a".repeat(64)}",
            timestamp = 0L,
            from = EthAddress("0x${"1".repeat(40)}"),
            to = EthAddress("0x${"2".repeat(40)}"),
            valueWei = "100".toBigInteger().toWei(),
            status = TransactionStatus.Confirmed,
        ),
    ).map { requireNotNull(it.toHistoryRow(EthAddress("0x${"1".repeat(40)}"))) }

    @Test
    fun loading_preserves_shimmers_for_uninitialized_empty_cache() {
        assertMapping(
            HistoryViewState.Loading,
            HistoryEvent.HistoryItemsListChange(emptyList(), false),
            HistoryViewState.Loading,
        )
    }

    @Test
    fun error_survives_delayed_uninitialized_empty_emission() {
        assertMapping(
            HistoryViewState.Error,
            HistoryEvent.HistoryItemsListChange(emptyList(), false),
            HistoryViewState.Error,
        )
    }

    @Test
    fun loading_and_error_show_initialized_empty_history() {
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            assertMapping(
                state,
                HistoryEvent.HistoryItemsListChange(emptyList(), true),
                HistoryViewState.Content(emptyList(), false),
            )
        }
    }

    @Test
    fun cached_items_override_loading_and_error_regardless_of_initialization() {
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            for (initialized in listOf(false, true)) {
                assertMapping(
                    state,
                    HistoryEvent.HistoryItemsListChange(items, initialized),
                    HistoryViewState.Content(items, false),
                )
            }
        }
    }

    @Test
    fun content_tracks_room_and_preserves_refresh_progress() {
        for (refreshing in listOf(false, true)) {
            for (initialized in listOf(false, true)) {
                for (updatedItems in listOf(emptyList(), items)) {
                    assertMapping(
                        HistoryViewState.Content(items, refreshing, pagination),
                        HistoryEvent.HistoryItemsListChange(updatedItems, initialized),
                        HistoryViewState.Content(updatedItems, refreshing, pagination),
                    )
                }
            }
        }
    }

    @Test
    fun load_control_events_preserve_existing_content() {
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            assertMapping(state, HistoryEvent.InitialLoadStarted, HistoryViewState.Loading)
            assertMapping(state, HistoryEvent.RefreshFailed, HistoryViewState.Error)
        }
        for (refreshing in listOf(false, true)) {
            val state = HistoryViewState.Content(items, refreshing, pagination)
            assertMapping(state, HistoryEvent.InitialLoadStarted, state)
            assertMapping(state, HistoryEvent.RefreshFailed, state)
        }
    }

    @Test
    fun refresh_and_pagination_events_apply_only_to_content() {
        val events = listOf(
            HistoryEvent.RefreshStarted,
            HistoryEvent.RefreshFinished,
            HistoryEvent.PaginationChange(pagination),
        )
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            events.forEach { assertMapping(state, it, state) }
        }
        for (refreshing in listOf(false, true)) {
            val state = HistoryViewState.Content(items, refreshing)
            assertMapping(state, HistoryEvent.RefreshStarted, state.copy(refreshing = true))
            assertMapping(state, HistoryEvent.RefreshFinished, state.copy(refreshing = false))
            assertMapping(state, HistoryEvent.PaginationChange(pagination), state.copy(pagination = pagination))
        }
    }

    private fun assertMapping(state: HistoryViewState, event: HistoryEvent, expected: HistoryViewState) {
        assertEquals(expected, historyReducer(state, event), "State: $state; event: $event")
    }
}
