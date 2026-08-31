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
