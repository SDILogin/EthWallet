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

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Remove the dispatcher from these callers. Make sure the called functions set the dispatcher where the blocking
  work happens.
- Keep `NonCancellable` where the current behavior needs it.

## Definition of done

- The three callers set no dispatcher, and no blocking work moves to the main thread.
- `./gradlew spotlessApply` and the shared tests pass.
