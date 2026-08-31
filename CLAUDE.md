# EthWallet

Kotlin Multiplatform Ethereum wallet for Android, Desktop, and iOS.

## Agent Board

- Never reference an Agent Board task by ID alone.
- Always include the exact task title after the ID, using the format `#20 Implement transfer submission orchestration`.
- Read the task through the Agent Board MCP before referencing it; do not guess, shorten, or paraphrase its title.
- Always use the Agent Board MCP for every board operation, including reading, creating, claiming, noting, updating, releasing, and completing tasks. Never use a browser, screen capture or recording, UI automation, direct HTTP, or the board database.

## Stack

- Jetpack Compose Multiplatform
- Koin for DI
- Kotlin Coroutines (always rethrow CancellationException)

## ViewModel Pattern

ViewModels use Events/State/News:
- `onEvent(Event)` - User actions
- `viewState: StateFlow` - UI state
- `news: Channel` exposed as `Flow` - One-time effects (navigation, toasts)

## ethwallet-kotlin Rust Interop

- `:ethwallet-kotlin` is the Kotlin Multiplatform Ethereum crypto library backed by Rust.
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

## Comments

Avoid comments for obvious behavior. Use KDoc sparingly when a declaration's contract is not clear from its signature. Rust `// SAFETY:` comments are allowed for unsafe operations and should state the binding or memory invariant being relied upon.

## Formatting

Run `./gradlew spotlessApply` before committing. Pre-push hook runs checks automatically.

For Rust changes, run these commands from `ethwallet-kotlin/rust`:

```bash
cargo fmt -- --check
cargo test
cargo clippy --all-targets -- -D warnings
```

Tests should let unexpected exceptions fail normally. Do not catch or translate failures unless the behavior under test specifically requires it.

## Live Sepolia UI E2E

The `SepoliaE2e` JUnit category is excluded from normal test tasks. Run it explicitly with:

```bash
./gradlew --no-daemon --no-build-cache -PsepoliaE2e=true \
  :app:sepoliaE2eTest \
  --console=plain
```

The ignored and untracked root `local.properties` must contain non-empty `seed_1` and `seed_2`
entries. This performs a real Sepolia transfer and spends test ETH. It requires macOS and the
host Rust JNI library; normal unit tests exclude this category. Never print these properties or
copy them into source code, BuildConfig, Gradle inputs, command-line arguments, generated assets,
test APKs, or logs. The test must interact only through the UI; its hardware-only Koin fakes must
not replace the real Ethereum, Room, ViewModel, repository, or UI components.
