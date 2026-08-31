# AGENTS.md

## Project

EthWallet is a Kotlin Multiplatform Ethereum wallet for Android, Apple Silicon macOS Desktop, and iOS.

Keep answers exact and clear. Be concise and give minimal full answers (e.g., in code reviews list only findings without fluff like "Key Strengths"). The project owner is an experienced Android developer, so avoid explaining basic Android SDK concepts unless asked.

## Agent Board

- Never reference an Agent Board task by ID alone.
- Always include the exact task title after the ID, using the format `#20 Implement transfer submission orchestration`.
- Read the task through the Agent Board MCP before referencing it; do not guess, shorten, or paraphrase its title.
- Always use the Agent Board MCP for every board operation, including reading, creating, claiming, noting, updating, releasing, and completing tasks. Never use a browser, screen capture or recording, UI automation, direct HTTP, or the board database.

## Modules

- `:shared`: KMP code. Most UI, ViewModels, business logic, models, DI, and shared data code live here.
  - `shared/src/commonMain`: cross-platform code.
  - `shared/src/androidMain`: Android-specific shared implementations.
  - `shared/src/desktopMain`: Desktop-specific shared implementations.
- `:app`: Android application entry point and Android-only wiring.
- `:desktopApp`: Compose Desktop entry point and packaging.
- `:ethwallet-kotlin`: Kotlin Multiplatform Ethereum crypto library backed by Rust.
  - `ethwallet-kotlin/src/commonMain`: public Kotlin API and shared validation.
  - `ethwallet-kotlin/src/androidMain`: Android JNI binding.
  - `ethwallet-kotlin/src/jvmMain`: Desktop JNI binding.
  - `ethwallet-kotlin/src/iosMain`: iOS C interop binding.
  - `ethwallet-kotlin/rust`: Rust implementation and C ABI.

## Stack

- Kotlin 2.x and Gradle Kotlin DSL.
- Android Gradle Plugin 9.x.
- Compose Multiplatform and Jetpack Compose.
- Koin for dependency injection.
- Kotlin Coroutines. Always rethrow `CancellationException`.
- Ktor for networking.
- Room and DataStore for local persistence.
- `ethwallet-kotlin` for Ethereum integration.
- Rust for cross-platform Ethereum cryptography.
- Spotless with ktlint for formatting.

## Architecture

- Prefer existing project patterns over new abstractions.
- ViewModels use Events/State/News:
  - `onEvent(Event)` for user actions.
  - `viewState: StateFlow` for screen state.
  - `news: Channel` (exposed as `Flow`) over `SharedFlow` for one-time effects such as navigation and toasts to prevent potential event loss.
- Put platform-independent logic in `:shared` when practical.
- Keep Android-only APIs in `:app` or `shared/src/androidMain`.
- Keep Desktop-only APIs in `:desktopApp` or `shared/src/desktopMain`.
- Do not move code across modules unless the requested change needs it.

## ethwallet-kotlin Rust Interop

- The public Kotlin API hides Rust, JNI, and C interop details.
- Ethereum transaction signing supports EIP-1559 only. Do not add legacy transaction support.
- Web3j may be used as a test-only compatibility oracle but must not be required by production signing.
- The Rust C ABI is private and is called only by the module's Kotlin bindings.
- Kotlin owns non-null guarantees, fixed input-buffer validation, and output-buffer allocation.
- Treat valid pointers, lengths, and output capacity as preconditions of unsafe Rust ABI functions. Do not add redundant native runtime checks for states excluded by the Kotlin bindings.
- Preserve semantic validation for externally sourced values, including mnemonics, private keys, and transaction fields.
- Keep raw-pointer unsafe operations explicit and localized at the FFI boundary.
- Copy private keys into fixed-size arrays and wrap owned secret copies in `Zeroizing`.
- The signing function writes output and output length only on success. Kotlin owns temporary-buffer cleanup.
- Keep the Rust function, C header, JNI bridge, iOS cinterop call, and tests synchronized when changing the ABI.
- Signing changes must retain byte-for-byte Web3j compatibility tests for both empty and non-empty calldata.

## Build And Test Commands

- Full build: `./gradlew build`
- Unit tests: `./gradlew test`
- Android instrumented tests: `./gradlew connectedAndroidTest`
- Formatting check: `./gradlew spotlessCheck`
- Apply formatting: `./gradlew spotlessApply`
- Android app build: `./gradlew :app:assembleDebug`
- Desktop run: `./gradlew :desktopApp:run`
- Rust formatting: from `ethwallet-kotlin/rust`, run `cargo fmt -- --check`
- Rust tests: from `ethwallet-kotlin/rust`, run `cargo test`
- Rust linting: from `ethwallet-kotlin/rust`, run `cargo clippy --all-targets -- -D warnings`

Before finishing code changes, run the narrowest useful Gradle task. If formatting may be affected, run `./gradlew spotlessApply` or `./gradlew spotlessCheck`.

## Style

- Follow `.editorconfig` and Spotless rules.
- Use trailing commas where the existing Kotlin style uses them.
- Keep changes focused. Do not perform broad refactors while fixing a narrow issue.
- Do not add comments for obvious code. Add short comments only for non-obvious behavior.
- Use ASCII unless the touched file already uses non-ASCII or the change requires it.
- Tests should let unexpected exceptions fail normally. Do not catch or translate failures unless the behavior under test specifically requires it.

## Safety

- Do not commit, amend, or rewrite git history unless explicitly asked.
- Do not revert user changes unless explicitly asked.
- Do not hardcode private keys, mnemonics, API tokens, or mainnet secrets.
- Be careful with Ethereum network defaults:
  - Debug builds use Sepolia.
  - Release builds use mainnet.
