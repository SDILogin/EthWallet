# EthWallet

Kotlin Multiplatform Ethereum wallet for Android and Desktop.

## Structure

- `shared/` - Common code (UI, ViewModels, business logic)
  - `commonMain/` - Cross-platform code
  - `androidMain/` - Android implementations
  - `desktopMain/` - Desktop implementations
- `app/` - Android entry point
- `desktopApp/` - Desktop entry point

## Stack

- Jetpack Compose Multiplatform
- Koin for DI
- Kotlin Coroutines (always rethrow CancellationException)

## ViewModel Pattern

ViewModels use Events/State/News:
- `onEvent(Event)` - User actions
- `viewState: StateFlow` - UI state
- `news: SharedFlow` - One-time effects (navigation, toasts)

## Comments

Single-line `//` comments are forbidden — never write them; delete them on sight when editing a file. KDoc is the only permitted comment form, used sparingly on declarations whose contract the signature cannot express.

## Formatting

Run `./gradlew spotlessApply` before committing. Pre-push hook runs checks automatically.