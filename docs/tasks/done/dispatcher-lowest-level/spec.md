# Task

#334 Set dispatchers at the lowest level

## Background

`REVIEW.md` says: "Dispatcher must be set on the lowest level". The work below does not run on Main, but the
dispatcher is set above the blocking call:

- `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/wallet/WalletsListViewModel.kt:48` injects `Dispatchers.IO` on
  the ViewModel level.
- `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/wallet/CreateWalletViewModel.kt:51` uses
  `withContext(NonCancellable + Dispatchers.IO)`.
- `shared/src/commonMain/kotlin/me/sdi/ethwallet/core/transfer/EthTransferInteractor.kt:119` wraps a call in
  `Dispatchers.Default`. The call already switches dispatcher: `KeyStoreEncryptor` on Android, `KekCipher` on
  Desktop.

Found in GitHub PR #27 for #10 Find long-running main thread operations; set up profiler and StrictMode.
Static review only. Nothing was measured. Severity: high is more than 16 ms on a frame. Medium grows with data size
or runs often. Low is a few ms or less, or one time at startup.

## Scope

- Remove the dispatcher from these callers. Make sure the called functions set the dispatcher where the blocking
  work happens.
- Keep `NonCancellable` where the current behavior needs it.
- Fix the `KekCipher` KDoc. It says that callers own threading, but both functions set `Dispatchers.Default`.
- Remove the lock in `CreateWalletViewModel` and the atomic in `WalletsListViewModel`. Their code now runs only on Main.
- In the UI flow tests, wait for the wallet label before the click. The wallets list now updates on Main a little later.

## Definition of done

- The three callers set no dispatcher, and no blocking work moves to the main thread.
- `./gradlew spotlessApply` and the shared tests pass.
