# Task

Add a transaction result screen that shows the current status of the sent transaction. Reuse the transaction
details screen if it fits.

## Background

After a send, the Send screen showed only a toast with the transaction hash. The user could not see whether the
transaction was confirmed or failed.

The transaction details screen shows a history transfer and its status from the history DB. The reviewer chose a
DB-first design: the sent transfer is saved to the history DB, and the details screen observes it by hash.

## Scope

- After a successful send, save the sent transfer to the history DB of the sender wallet with status Pending, the
  local send time, and the estimated fee `maxFeePerGas * gasUnits`. Then open transaction details. It replaces the
  Send screen in the back stack, so back opens the wallet dashboard. A failed DB write is logged; the transaction
  is already sent.
- `TransferHistoryRepository.observe(trxHash)` returns the transfer by chain ID and hash only. A hash can have
  several rows (one per wallet and per log index). The query returns the row with the lowest log index, and a row
  with a fee before a row without one.
  - A transaction with several token transfers opens the same transfer from each History item.
  - A transfer between two own wallets shows the sender row, because only the sender row has a fee.
- Transaction details polls the Blockscout transaction by hash while the transfer is Pending. A 404 means
  "not indexed yet" and keeps Pending. A final status updates all rows of the hash (`update`). Lookup errors keep
  the last status and poll again.
- A sent token transfer has no log index before mining. It is saved with `UNKNOWN_LOG_INDEX` (-1). An ETH transfer
  uses the same index, but a token transfer sent by the app has zero ETH value, so it has no ETH row with the same
  key. History refresh deletes the placeholder after it saves a token transfer with the same hash.
- Some sent transfers are never listed by Blockscout: a reverted token transfer has no log, and ETH sent to a
  contract is filtered out. After it saves the fetched pages, History refresh reads the status of each cached
  Pending transfer that the pages do not contain. A final status updates the rows of the hash.
- Refresh writes in several steps, not in one DB batch. A failed step is repaired by the next refresh.
- The local send time differs from the history rule "do not invent a timestamp". The Blockscout row replaces it
  after indexing.
- Update the live Sepolia E2E tests: after a send they open the details screen. Desktop waits for "Confirmed".

## Out of scope

- A dropped or replaced transaction stays Pending. Each refresh pages back to it and reads its status again.
- Background tracking when no screen is open.

## Definition of done

- The details screen shows the sent transfer and its live status on Android and Desktop.
- Unit tests cover the saved transfer, polling (missing row, not indexed, Confirmed, Failed, errors,
  resubscription), the status lookup and placeholder cleanup in refresh, and the new Room queries.
- `./gradlew spotlessApply`, shared tests, and the app and desktop test compilation pass.
