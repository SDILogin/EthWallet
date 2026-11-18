# Task

#330 Move Android binder calls in PIN and biometric code off the main thread

## Background

These suspend functions make binder calls on the caller thread. ViewModels call them on Main. This breaks the
`REVIEW.md` rule "suspend function must be safe to call from main thread".

- `shared/src/androidMain/kotlin/me/sdi/ethwallet/core/security/AndroidBiometricUnlock.kt:91`:
  `BiometricManager.canAuthenticate`. Called from `VerifyPinCodeViewModel`, `SettingsViewModel` and
  `SetPinCodeViewModel`.
- `shared/src/androidMain/kotlin/me/sdi/ethwallet/core/security/KeyStorePinAttemptsGuard.kt:44`:
  `Settings.Global.getInt(BOOT_COUNT)` is a `ContentResolver` call. Called from `freshState()`, `normalize()` and
  `onFailedAttempt()` outside `withContext(dispatcher)`.

Estimated severity: low. Platform: Android.

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Run `canAuthenticate` and the boot count read inside the class dispatcher, at the lowest level.

## Definition of done

- The two calls do not run on the main thread.
- `./gradlew spotlessApply` and the shared Android tests pass.
