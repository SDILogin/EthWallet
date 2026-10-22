# EthWallet

EthWallet is an Ethereum wallet for Android and Apple Silicon macOS, built with Kotlin Multiplatform and Compose.

## Project Structure

The project is organized into the following modules:

- **`:app`**: The primary Android application module containing the UI code (Jetpack Compose) and Android-specific implementations.
- **`:shared`**: Business logic, data models, and platform-agnostic code shared by Android and desktop.
- **`:desktopApp`**: The Apple Silicon macOS desktop entry point and DMG packaging.

## Prerequisites

- **JDK 11** or higher.
- **Android Studio** (latest stable release recommended).
- **Kotlin**.

## getting Started

### Build the Project
To build the project, run:
```bash
./gradlew build
```

### Run Tests
To execute unit tests:
```bash
./gradlew test
```

To run instrumented (Android) tests:
```bash
./gradlew connectedAndroidTest
```

### Compose Hot Reload MCP

The project-scoped Codex configuration in `.codex/config.toml` starts the experimental
Compose Hot Reload MCP server for `:desktopApp`. Open this repository as a trusted
project and restart Codex after adding or changing the configuration.

Start the desktop app with Hot Reload in a separate terminal:

```bash
./gradlew :desktopApp:hotRunDesktop
```

Codex can then inspect the Compose semantic tree, capture screenshots, click, type,
scroll, and read runtime logs. The MCP server waits for the app and reconnects when
it restarts. Codex starts the server automatically; do not launch a second server manually.

### Desktop UI smoke test

```bash
./gradlew :desktopApp:desktopTest
```

The test renders the desktop Compose UI and checks onboarding and navigation to PIN setup.
It uses temporary PIN storage and requires no wallet seeds or network access.

### Live Sepolia UI E2E

The live wallet-transfer test is a Robolectric Android host test that uses the real application UI,
Room database, and Ethereum implementation. It is excluded from normal test tasks because every
successful run sends Sepolia ETH and spends testnet funds.

Add the two funded test-wallet recovery phrases to the ignored root `local.properties` file:

```properties
seed_1=<funded sender recovery phrase>
seed_2=<receiver recovery phrase>
```

Do not add quotes around the phrases. The Gradle preflight verifies that `local.properties` is
ignored and untracked without printing either value. Run the test only on Apple Silicon macOS:

```bash
./gradlew --no-daemon --no-build-cache -PsepoliaE2e=true \
  :app:sepoliaE2eTest \
  --console=plain
```

The opt-in task builds the host Rust JNI library and passes only the absolute `local.properties`
path to the test JVM. Seed phrases are read at test runtime and are never placed in BuildConfig,
generated sources, command-line arguments, Gradle inputs, test APKs, or logs. The test requires
Apple Silicon macOS and the host Rust JNI library; normal unit tests exclude this category.

The desktop version uses the same `seed_1` and `seed_2` configuration:

```bash
./gradlew -PsepoliaE2e=true :desktopApp:sepoliaE2eTest --console=plain
```

It runs the desktop Compose UI with desktop crypto, software PIN storage, and private-key encryption.
PIN preferences use a temporary directory, and wallets use the desktop in-memory database.
The scenario imports both wallets, sends `0.000001` Sepolia ETH, and checks the receiver's balance.
Each run spends testnet funds. Normal `:desktopApp:desktopTest` runs exclude this test, even with
`-PsepoliaE2e=true`. Live test results are never reused from the Gradle cache.

### Code Formatting
This project uses **Spotless** and **ktlint** to enforce code style.

Check formatting:
```bash
./gradlew spotlessCheck
```

Apply formatting automatically:
```bash
./gradlew spotlessApply
```

**Note:** It is recommended to run `./gradlew spotlessKotlinApply spotlessKotlinCheck` before committing changes.

## Architecture

The application defines a clear separation of concerns, aiming for a Clean Architecture approach:

- **UI Layer**: Jetpack Compose components.
- **ViewModel Layer**: State management.
- **Domain/Data Layer**: Located primarily in the `:shared` module or structured within `:app` for now, handling business logic and data access.

## Contribution

Code style rules are defined in:
- `.editorconfig`
- `tools/ethwallet-codestyle.xml`

For more details on working with this repository, refer to `CLAUDE.md`.
