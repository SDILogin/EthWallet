# Task

#333 Start ReceiveQrViewModel work in viewState.onStart

## Background

`shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/receive/ReceiveQrViewModel.kt:70`: `init` launches four flows with
`launchIn(viewModelScope)`. This breaks the `REVIEW.md` rule "ViewModel `init` must be fast and error-safe. Actual
initialization must be triggered from inside `viewState.onStart {}`".

Estimated severity: low. Platforms: Android and Desktop.

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Start the flows from `viewState.onStart {}`. Keep the current behavior.

## Definition of done

- `ReceiveQrViewModel.init` launches no work.
- `./gradlew spotlessApply` and the shared tests pass.
