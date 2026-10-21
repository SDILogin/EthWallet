# Task

Load history from Blockscout and display user transfers on the history screen.

## Scope

- `TransfersHistoryProvider.kt`: domain representation of native ETH and ERC-20 transfers
  (`sealed interface Transfer`).
- `TransfersHistoryProviderBlockscout`: implementation of `TransfersHistoryProvider`. Network DTOs are declared
  inside this implementation.
- `TransferHistoryRepository.kt`: repository that hides the history data source.
- `TransferHistoryDao` and `TransferHistoryEntity`: local DB schema. Schema changes do not require a migration
  because the app is not in production yet and will be reinstalled.
- `HistoryUpdateInteractor`: loads and saves history, including refresh and pagination.
- `HistoryViewModel` and the history screen: presentation logic and UI.

## Domain rules

The target audience is regular users without deep blockchain knowledge. The UI presents fund movements as history
operations and hides the distinction between blockchain transactions and transfers.

- Show one history item for each incoming or outgoing ETH or ERC-20 fund transfer involving the wallet, regardless
  of the block containing it. Incoming transfers can be initiated by another user.
- Multiple ERC-20 transfers in one transaction are separate operations. Each transfer has its own history item and
  DB record.
- Support native ETH and ERC-20 fund transfers. ERC-20 transfers are supported even though they use smart contracts.
  Other smart contract operations are outside the scope.
- Exclude zero-value transfers, failed attempts, contract creation, and internal ETH transfers from the history screen.
- Each outgoing history item shows the transfer amount and the full transaction fee. If several outgoing ERC-20
  transfers belong to one transaction, each item shows that same full fee. Do not divide the fee between transfers.
  Label it "Transaction fee": fees belong to transactions, not blocks.
- Incoming history items show the transfer amount without a fee. Sender, recipient, and time are available for both
  incoming and outgoing transfers.
- A self-transfer moves funds between accounts displayed in the app, including a transfer to the same account.

### Timestamps

Domain and DB timestamps are non-null `Long` values in epoch milliseconds, matching the existing provider conversion.
The API is expected to provide a valid timestamp for every operation, including pending operations. Treat this as
required response data and parse it when mapping DTOs to domain transfers. Do not invent a fallback timestamp.

### Token decimals

Use the token decimals returned by the API when available. If the API does not provide decimals, use `6` as the
fallback decimal count to format the transfer amount. This is an application fallback, not an ERC-20 standard default.

## Loading and pagination

### Network format

```json
{
  "items": [],
  "next_page_params": {
    "block_number": 18678766,
    "index": 119,
    "items_count": 50
  }
}
```

Response payloads are inside the `items` array. Each item is parsed using an `@Serializable` network DTO.
`@SerialName` values must match response keys. Response examples are listed in References.

Each endpoint has its own cursor. Pass all values from its `next_page_params` as a `Map<String, String>` in the next
request. Do not pass one endpoint's cursor to the other endpoint. An exhausted source is not queried again during
older-page loading.

There are two sources:

- `/addresses/$address/transactions`: native ETH transfers.
- `/addresses/$address/token-transfers`: ERC-20 transfers.

The provider combines their results so the UI displays one list of user operations.

### Initial loading

If the DB is empty or a source has no cached operations, load only the first page from each source. Do not load
additional pages to find overlap in this case. Save the combined results atomically and retain both next-page cursors
for older-page loading.

### Refresh and overlap

For a history with cached operations from both sources, refresh starts from the first page of both sources.
Determine the refresh boundaries against the DB state captured before the refresh starts.

- If there are no in-progress operations, load any new operations until the responses reach already cached history.
  If there are no new operations, the displayed history remains unchanged.
- If there are in-progress operations, use the earliest in-progress operation's timestamp as the refresh boundary.
- Load all operations at or after that boundary. The boundary is inclusive: include the oldest in-progress operation
  and all operations with the same timestamp. Continue through pages sharing the boundary timestamp until the range
  has been covered or the source is exhausted.
- Do not stop at an already cached operation if the accumulated range has not yet reached the boundary.
- Completion is evaluated separately for each source. Refresh succeeds only after both sources finish their required
  loading successfully.
- Apply returned statuses to the matching DB records so in-progress operations can move to a terminal state.
  Pending operations that become failed must have their tracked status updated, but must no longer appear in the
  history screen.

Merge the accumulated results and insert or update them in one atomic DB operation. The history query supplies
the display order.
If any required request fails, the refresh fails and the DB remains unchanged. No partial results are displayed.
If a source is exhausted before overlap is found, that source is complete. Do not request more pages or restart it;
use its accumulated results. Completion still requires both sources to succeed.

### Loading older pages

Loading older pages queries each source that still has more items. Once one source is exhausted, query only the other
source. Both refresh and older-page loading are atomic: if any required request fails, leave the DB unchanged and
preserve the loading position for retry.

For example, if ERC-20 history contains 10 items and native ETH history contains 1000 items, the initial load queries
both sources. After the ERC-20 source is exhausted, older-page loading queries only the native ETH source.

Save the combined results to the DB and order the entire saved history by timestamp descending in the SQL query,
keeping the existing pending-first grouping. A later page may insert newly discovered operations between existing
rows. This is expected; the full list must remain sorted after each DB update. No extra buffering or network requests
are required solely to prevent these insertions.

Reopening the history screen restarts network pagination from the first page. Previously saved DB records remain
available to the screen.

## DB

Use one entity format for both native ETH and ERC-20 transfers. Type-specific columns may be null, but timestamps
are non-null.

Each record represents one transfer from the user's point of view. For example, two ERC-20 transfers in one blockchain
transaction produce two DB records and two history items.

The DB is the single source of truth for the history screen. The ViewModel observes the repository and reacts to DB
changes. The update interactor saves loaded transfers through the repository. Failed refreshes and failed older-page
loads must not change the DB.

Match returned operations using the existing DB identity: wallet, network, transaction hash, and log index.
Native ETH transfers use the existing native-transfer log-index sentinel. ERC-20 transfers use their actual log index
so multiple transfers in one transaction remain separate. Repeated loads update matching records rather than creating
duplicates. The timestamp controls the refresh range; it is not the operation's identity.

## UI

- Scrolling to the bottom triggers loading older history.
- Show a loading indicator as the last list item while an older-page request is running.
- If loading older history fails, replace that indicator with an error message and a Retry button at the bottom of
  the list. Keep existing rows visible. Retry uses the same cursors; failed requests must not advance pagination.
- Stop automatic loading when both sources are exhausted. An empty or fully filtered page does not mean the source
  is exhausted if it has a next-page cursor.
- Pull-to-refresh loads new operations and updates in-progress statuses.
- New and updated operations become visible only after all required loading succeeds and the combined results are
  saved to the DB.
- Keep previously displayed DB records visible when loading fails.

## Implementation details

Use existing project patterns for fee lookup, metadata validation, initial-load and refresh errors, concurrent request
handling, and platform persistence. The rules above define the agreed history behavior; these implementation details
do not change the transfer scope, refresh boundaries, or pagination rules.

## DOD

- All relevant tests and static analysis pass.
- Every eligible transfer in the loaded history appears exactly once. Excluded operations do not appear. Raw response
  item counts do not need to equal visible row counts because filtering and deduplication apply.
- Multiple ERC-20 transfers in one transaction remain separate history items. Every outgoing item shows the full
  transaction fee.
- With an empty DB or a source without cached operations, loading requests only the first page of both sources and
  retains their cursors for pagination.
- Refresh of cached history covers existing in-progress operations before publishing results, unless a source is
  exhausted. Exhaustion completes that source even without overlap.
- Refresh and older-page loading leave the DB unchanged if any required request fails.
- The SQL query sorts the combined history by timestamp descending within the existing pending-first grouping.
  Pagination stops querying exhausted sources and continues through empty or fully filtered pages when a cursor exists.
- Pagination failures show an error and Retry button at the bottom of the list. Retry does not skip pages.
- Missing token decimals use the six-decimal fallback. Timestamps remain required, non-null epoch milliseconds.
- Reopening the screen restarts network pagination while retaining cached DB records.

## References

- [token_transfers_response.json](token_transfers_response.json): ERC-20 tExransfer response example
  (`/addresses/$address/token-transfers`). Use it to declare the network DTO.
- [transactions_response.json](transactions_response.json): transaction response example
  (`/addresses/$address/transactions`). Use it to declare the DTO for native ETH transfers.
