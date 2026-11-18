# Task

#321 Show popup messages on Desktop

Desktop `ToastManager` only wrote messages to the log, and logging is off on Desktop. The user saw nothing.
Desktop now shows each message as a short Material snackbar in the app window. Android keeps its system toast.

## Scope

- `ToastManager`, `rememberToastManager` and the new `ToastHost(content)` live in `shared/src/*/kotlin/me/sdi/ethwallet/util/`.
- `ToastHost` wraps `NavDisplay` in `EthWalletApp`, inside `EthWalletTheme`. On Android it only calls `content()`.
- Desktop `ToastHost` holds one `SnackbarHostState` and its own coroutine scope. It provides the Desktop
  `ToastManager` to `rememberToastManager` through a composition local.
- `ReceiveScreenContentPreview` is wrapped in `ToastHost`, because `AddressDisplay` calls `rememberToastManager`.
- The call sites are unchanged: History, Receive, Send, Change PIN, Tokens list, Verify PIN, Wallet, Wallets list.

## Behavior

- The snackbar uses `SnackbarDuration.Short` and has no action, so it hides by itself.
- It is drawn over the content at the bottom center. It does not move other UI elements.
- The host scope outlives screens. A message stays visible when the screen that sent it closes, for example
  Change PIN success.
- Messages queue. Each one shows after the previous one hides.
- A message shown while a dialog is open is drawn below the dialog scrim.

## Out of scope

- The hard-coded "Something went wrong" text in `VerifyPinCodeScreen`.
- User-visible text inside news (`SendNews`, `ReceiveQrNews`).
- Any Android behavior change.

## Verification

- `DesktopToastTest` checks that a message shows, does not move other content, and hides by itself.
