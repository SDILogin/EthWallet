package me.sdi.ethwallet.ui.history

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.HistoryItem
import me.sdi.ethwallet.core.history.TransactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryReducerTest {
    private val items = listOf(
        HistoryItem.NativeTransfer(
            transactionHash = "0x${"a".repeat(64)}",
            timestamp = null,
            from = EthAddress("0x${"1".repeat(40)}"),
            to = EthAddress("0x${"2".repeat(40)}"),
            valueWei = "100",
            status = TransactionStatus.Confirmed,
        ),
    ).map { requireNotNull(it.toHistoryRow(EthAddress("0x${"1".repeat(40)}"))) }

    @Test
    fun loading_preserves_shimmers_for_uninitialized_empty_cache() {
        assertMapping(HistoryViewState.Loading, HistoryEvent.HistoryItemsListChange(emptyList(), false), HistoryViewState.Loading)
    }

    @Test
    fun error_survives_delayed_uninitialized_empty_emission() {
        assertMapping(HistoryViewState.Error, HistoryEvent.HistoryItemsListChange(emptyList(), false), HistoryViewState.Error)
    }

    @Test
    fun loading_and_error_show_initialized_empty_history() {
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            assertMapping(state, HistoryEvent.HistoryItemsListChange(emptyList(), true), HistoryViewState.Content(emptyList(), false))
        }
    }

    @Test
    fun cached_items_override_loading_and_error_regardless_of_initialization() {
        for (state in listOf(HistoryViewState.Loading, HistoryViewState.Error)) {
            for (initialized in listOf(false, true)) {
                assertMapping(state, HistoryEvent.HistoryItemsListChange(items, initialized), HistoryViewState.Content(items, false))
            }
        }
    }

    @Test
    fun content_tracks_room_and_preserves_refresh_progress() {
        for (refreshing in listOf(false, true)) {
            for (initialized in listOf(false, true)) {
                for (updatedItems in listOf(emptyList(), items)) {
                    assertMapping(
                        HistoryViewState.Content(items, refreshing),
                        HistoryEvent.HistoryItemsListChange(updatedItems, initialized),
                        HistoryViewState.Content(updatedItems, refreshing),
                    )
                }
            }
        }
    }

    @Test
    fun loading_control_event_mappings() {
        val expected = listOf(
            HistoryEvent.HistoryUpdateOnStartFailed to HistoryViewState.Error,
            HistoryEvent.RetryInitialLoadFail to HistoryViewState.Error,
            HistoryEvent.RetryInitialLoadStart to HistoryViewState.Loading,
            HistoryEvent.P2RefreshStart to HistoryViewState.Loading,
            HistoryEvent.P2RefreshFinish to HistoryViewState.Loading,
            HistoryEvent.InitialLoadingFlagChange(false) to HistoryViewState.Loading,
            HistoryEvent.InitialLoadingFlagChange(true) to HistoryViewState.Loading,
        )
        expected.forEach { (event, result) -> assertMapping(HistoryViewState.Loading, event, result) }
    }

    @Test
    fun only_retry_start_moves_error_to_loading() {
        val expected = listOf(
            HistoryEvent.HistoryUpdateOnStartFailed to HistoryViewState.Error,
            HistoryEvent.RetryInitialLoadFail to HistoryViewState.Error,
            HistoryEvent.RetryInitialLoadStart to HistoryViewState.Loading,
            HistoryEvent.P2RefreshStart to HistoryViewState.Error,
            HistoryEvent.P2RefreshFinish to HistoryViewState.Error,
            HistoryEvent.InitialLoadingFlagChange(false) to HistoryViewState.Error,
            HistoryEvent.InitialLoadingFlagChange(true) to HistoryViewState.Error,
        )
        expected.forEach { (event, result) -> assertMapping(HistoryViewState.Error, event, result) }
    }

    @Test
    fun content_control_events_preserve_items_and_only_p2r_changes_indicator() {
        for (contentItems in listOf(emptyList(), items)) {
            for (refreshing in listOf(false, true)) {
                val state = HistoryViewState.Content(contentItems, refreshing)
                val ignored = listOf(
                    HistoryEvent.HistoryUpdateOnStartFailed,
                    HistoryEvent.RetryInitialLoadFail,
                    HistoryEvent.RetryInitialLoadStart,
                    HistoryEvent.InitialLoadingFlagChange(false),
                    HistoryEvent.InitialLoadingFlagChange(true),
                )
                ignored.forEach { assertMapping(state, it, state) }
                assertMapping(state, HistoryEvent.P2RefreshStart, state.copy(refreshing = true))
                assertMapping(state, HistoryEvent.P2RefreshFinish, state.copy(refreshing = false))
            }
        }
    }

    private fun assertMapping(state: HistoryViewState, event: HistoryEvent, expected: HistoryViewState) {
        assertEquals(expected, historyReducer(state, event), "State: $state; event: $event")
    }
}
