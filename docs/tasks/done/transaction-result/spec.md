# Task

Add a transaction result screen that shows the current status of the sent transaction. Reuse the transaction
details screen if it fits.

## Background

After a send, the Send screen shows only a toast with the transaction hash. The user can not see whether the
transaction is confirmed or failed.

The transaction details screen shows a history transfer and its status. Its ViewModel observes one history DB row
by `(wallet, hash, logIndex)`. After a send there is no such row. For an ERC-20 send the log index is unknown until
the transaction is mined, and a reverted ERC-20 transfer never appears in the Blockscout token transfers. So the
history DB can not be the status source for a sent transaction.

## Scope

- After a successful send, open the result screen. It replaces the Send screen in the back stack, so back opens the
  wallet dashboard.
- The result screen reuses the transaction details UI and `TransactionDetailsState`. It keeps the
  "Transaction details" title.
- `TransactionResultViewModel` starts from the sent transfer (status Pending, estimated fee) and polls the Blockscout
  transaction by hash until the status is Confirmed or Failed. A 404 means "not indexed yet" and keeps Pending.
  Lookup errors keep the last status and poll again.
- The sent transfer is carried in the navigation destination. It is not written to the history DB.
- Update the live Sepolia E2E tests: after a send they wait for "Confirmed" on the result screen.

## Out of scope

- Writing the sent transfer to the history DB or refreshing history from the result screen.
- Tracking the transaction after the screen closes.
- A dropped or replaced transaction stays Pending while the screen is open.

## Definition of done

- The result screen shows the sent transfer and its live status on Android and Desktop.
- Unit tests cover Pending to Confirmed, Pending to Failed, not indexed, errors, and resubscription.
- `./gradlew spotlessApply`, shared tests, and the app and desktop test compilation pass.
