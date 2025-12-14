# EthWallet

EthWallet is an Android application built using Kotlin and Jetpack Compose that serves as an Ethereum wallet. This project adheres to modern Android development practices and is structured to support Kotlin Multiplatform (KMP) via a shared module.

## Project Structure

The project is organized into the following modules:

- **`:app`**: The primary Android application module containing the UI code (Jetpack Compose) and Android-specific implementations.
- **`:shared`**: A Kotlin Multiplatform module designed to house business logic, data models, and platform-agnostic code shared between targets (currently Android, potentially iOS/Desktop in the future).

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
