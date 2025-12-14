# Shared Module (`:shared`)

This module is a **Kotlin Multiplatform** library containing common business logic, data models, and networking code shared across potential platform targets.

## Targets
Currently configured targets:
- **Android**: Compiles to an AAR for use in the `:app` module.
- **Common**: Platform-agnostic Kotlin code.

(Note: iOS and other targets can be added here in the future).

## Structure
- `src/commonMain`: Code shared across all platforms.
- `src/androidMain`: Android-specific implementations types expected by common code.

## Usage
The `:app` module depends on this project. Changes here will strictly affect the main application's logic.
