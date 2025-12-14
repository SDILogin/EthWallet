# App Module (`:app`)

This module contains the Android application source code for EthWallet.

## Features
- **UI**: Built entirely with [Jetpack Compose](https://developer.android.com/jetpack/compose).
- **Navigation**: Uses Jetpack Navigation Compose.
- **State Management**: ViewModels and Flows.

## Project Structure
- `src/main/java/me/sdi/ethwallet`: Root package.
  - `ui/`: Compose screens and components.
  - `viewmodel/`: ViewModels associated with UI screens.
  - `di/`: Dependency Injection structure (if applicable).

## Testing
Unit tests are located in `src/test`.
Instrumented tests (UI tests) are located in `src/androidTest`.
