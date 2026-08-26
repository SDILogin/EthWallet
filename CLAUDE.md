# EthWallet

Kotlin Multiplatform Ethereum wallet for Android and Desktop.

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
