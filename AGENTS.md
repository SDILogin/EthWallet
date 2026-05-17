# AGENTS.md

## Project

EthWallet is a Kotlin Multiplatform Ethereum wallet for Android and Desktop.

Keep answers exact and clear. The project owner is an experienced Android developer, so avoid explaining basic Android SDK concepts unless asked.

## Modules

- `:shared`: KMP code. Most UI, ViewModels, business logic, models, DI, and shared data code live here.
  - `shared/src/commonMain`: cross-platform code.
  - `shared/src/androidMain`: Android-specific shared implementations.
  - `shared/src/desktopMain`: Desktop-specific shared implementations.
- `:app`: Android application entry point and Android-only wiring.
- `:desktopApp`: Compose Desktop entry point and packaging.

## Stack

- Kotlin 2.x and Gradle Kotlin DSL.
- Android Gradle Plugin 9.x.
- Compose Multiplatform and Jetpack Compose.
- Koin for dependency injection.
- Kotlin Coroutines. Always rethrow `CancellationException`.
- Ktor for networking.
- Room and DataStore for local persistence.
- Web3j for Ethereum integration.
- Spotless with ktlint for formatting.

## Architecture

- Prefer existing project patterns over new abstractions.
- ViewModels use Events/State/News:
  - `onEvent(Event)` for user actions.
  - `viewState: StateFlow` for screen state.
  - `news: SharedFlow` for one-time effects such as navigation and toasts.
- Put platform-independent logic in `:shared` when practical.
- Keep Android-only APIs in `:app` or `shared/src/androidMain`.
- Keep Desktop-only APIs in `:desktopApp` or `shared/src/desktopMain`.
- Do not move code across modules unless the requested change needs it.

## Build And Test Commands

- Full build: `./gradlew build`
- Unit tests: `./gradlew test`
- Android instrumented tests: `./gradlew connectedAndroidTest`
- Formatting check: `./gradlew spotlessCheck`
- Apply formatting: `./gradlew spotlessApply`
- Android app build: `./gradlew :app:assembleDebug`
- Desktop run: `./gradlew :desktopApp:run`

Before finishing code changes, run the narrowest useful Gradle task. If formatting may be affected, run `./gradlew spotlessApply` or `./gradlew spotlessCheck`.

## Style

- Follow `.editorconfig` and Spotless rules.
- Use trailing commas where the existing Kotlin style uses them.
- Keep changes focused. Do not perform broad refactors while fixing a narrow issue.
- Do not add comments for obvious code. Add short comments only for non-obvious behavior.
- Use ASCII unless the touched file already uses non-ASCII or the change requires it.

## Safety

- Do not commit, amend, or rewrite git history unless explicitly asked.
- Do not revert user changes unless explicitly asked.
- Do not hardcode private keys, mnemonics, API tokens, or mainnet secrets.
- Be careful with Ethereum network defaults:
  - Debug builds use Sepolia.
  - Release builds use mainnet.
