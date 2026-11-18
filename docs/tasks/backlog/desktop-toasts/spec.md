# Task

Show popup messages (toasts) on Desktop. Today Desktop does not show them.

## Background

- Screens show short messages through `ToastManager.showToast` (`shared/src/commonMain/kotlin/me/sdi/ethwallet/util/ToastManager.kt`).
- Android shows a system toast.
- Desktop (`shared/src/desktopMain/kotlin/me/sdi/ethwallet/util/ToastManager.kt`) only writes the message to the log.
  Logging is off on Desktop, so the user sees nothing.
- Example: on the History screen, a failed refresh must show "Could not update history. Pull down to try again."
  On Desktop, nothing happens.
- These screens call `showToast`: History, Receive, Send, Change PIN, Tokens list, Verify PIN, Wallet, Wallets list.

## Scope

- Show each message on Desktop as a short popup in the app window, for example a Material snackbar.
- The message hides by itself after a short time.
- A new message replaces or follows the current message. It must not hide below other content.
- The popup must not move other UI elements (REVIEW.md: a state change must not move other UI elements).
- Keep the Android behavior.

## Verification

- `./gradlew spotlessApply`, shared compilation, shared tests.
- A Desktop UI test that shows a message and finds its text.
- Manual check on Apple Silicon macOS: open History of a loaded wallet, turn off Wi-Fi, refresh the history, see the message.
