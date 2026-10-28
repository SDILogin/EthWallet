# Task

Allow an unlocked user to change their PIN from the Settings screen.

## Scope

- `PinCodeStorage`: the PIN becomes an app-lock check and no longer takes part in encrypting the master
  KEK. New `changePinCode(newPin)` operation. Implemented by `PinCodeStorageDataStore`
  (`shared/src/androidMain`) and `PinCodeStorageSoftware` (`shared/src/commonMain`, used by Desktop).
  There is no iOS source set. The test-only implementations are updated too: `FakePinCodeStorage`
  (`shared/src/commonTest`), the anonymous `object : PinCodeStorage` in
  `app/src/test/java/me/sdi/ethwallet/flow/WalletFlowTest.kt`, and `InMemoryPinCodeStorage` in
  `app/src/test/java/me/sdi/ethwallet/flow/e2e/environment/SepoliaE2eEnvironment.kt`.
- `SettingsScreen`: a "Change PIN" row that opens the flow through `onChangePinClick: () -> Unit`, wired in
  `EthWalletApp`.
- `ChangePinCodeScreen` and `ChangePinCodeViewModel` in `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/settings/`,
  reached through `Destination.ChangePinCodeScreen` with the same `extraPane()` metadata as
  `Destination.SettingsScreen`.
- Compose string resources in `shared/src/commonMain/composeResources/values/strings.xml`.

The onboarding `SetPinCodeScreen`, the lock screen (`VerifyPinCodeViewModel`, including its use of
`PinAttemptsGuard`), biometric unlock, and the PIN length (6 digits) are unchanged.

## Why the PIN is not part of the encryption envelope

Anyone with access to the app process or its storage can brute force a 6-digit PIN offline; the attempt
guard only runs inside the app. Wrapping the KEK with a PIN-derived key added complexity and the illusion
of extra protection. The PIN therefore only gates the UI.

## Storage design

The random 256-bit master KEK is kept. `KeyStoreEncryptor`, `SoftwarePrivateKeyEncryptor` and
`AndroidBiometricUnlock` (`bio_wrapped_kek`) consume it through `PinSession` exactly as before.

- **Android** (`PinCodeStorageDataStore`): the KEK is encrypted only by the AndroidKeyStore AES-GCM key
  `wallet_pin_code_key` and stored as `keystore_wrapped_kek`. `hasPinCode()` requires the verifier, its
  salt, the wrapped KEK and the key alias.
- **Desktop** (`PinCodeStorageSoftware`): the KEK is stored as Base64 (`master_kek`) in the owner-only
  `pinArtifactsDataStore`. File permissions are its only protection.
- **PIN verifier** (both platforms): `pin_verifier` is PBKDF2WithHmacSHA256(PIN, `pin_verifier_salt`) with
  the existing parameters (200 000 iterations, 256-bit output, 16-byte random salt). It is compared with
  `MessageDigest.isEqual`.

There is no migration. The preference keys of the old PIN-wrapped format (`pin_kdf_salt`,
`pin_wrapped_kek`) are not read, so an old install reads as "no PIN set". `DataCleanerDataStore` and
`DataCleanerPinArtifacts` clear their whole stores and `DataCleanerAndroidKeyStore` deletes every alias,
so logout also removes the new keys.

### `verifyPinCode(pinCode): Boolean`

- Precondition: a PIN is stored (the lock screen is shown only after the storage check); otherwise
  `checkNotNull` throws `IllegalStateException`.
- Derive the verifier from `pinCode` and the stored salt. On a mismatch return `false`.
- On a match load the KEK (Android: decrypt with the keystore key), call `pinSession.set(kek)` and
  `userActivityManager.onAppUnlocked()`, return `true`.
- A failure to load the KEK after a matching PIN is not a wrong PIN; it propagates as an exception and is
  handled by `VerifyPinCodeViewModel` as "Something went wrong", not as a failed attempt.

### `changePinCode(newPin)`

- Precondition: a PIN is stored (Settings is reachable only while unlocked); otherwise `checkNotNull`
  throws `IllegalStateException`.
- Generate a fresh salt, derive the verifier for `newPin`, and write both in a **single**
  `dataStore.edit`, so process death leaves either the old or the new PIN valid. The presence check runs
  inside the same edit.
- The KEK, wallet keys, biometric artifacts, `PinSession` and `PinAttemptsGuard` are not touched.

## Flow

1. **Settings**: a "Change PIN" row between the biometric row and the "Lock app" row.
2. **Enter new PIN**: titled "Enter new PIN". The current PIN is not asked for: the user already unlocked
   the app to reach Settings.
3. **Confirm new PIN**: titled "Confirm new PIN". On a mismatch, show "PINs don't match. Try again.",
   clear both entries, return to step 2 and shake the indicator. On a match call `changePinCode`; the
   keypad is disabled while saving.
4. **Success**: emit `ChangePinCodeNews.PinChanged`; the screen shows "PIN changed" through
   `rememberToastManager().showToast(...)` and pops back to Settings.

If `changePinCode` throws, the flow restarts at step 2 with "Something went wrong" and a shake. Back on any
step returns to Settings without changes. If the session locks while the flow is open, the existing
`EthWalletApp` lock-screen redirect clears the back stack.

## Implementation details

- Events/State/News: `onEvent(ChangePinCodeEvent)`, `viewState: StateFlow<ChangePinCodeViewState>`, `news`
  as a `Channel` exposed as `Flow`.
- `ChangePinCodeViewModel` depends only on `PinCodeStorage` and is registered in `commonModule`.
- Errors are a `ChangePinCodeError` enum in the state, mapped to string resources in the screen.
- The indicator shake is a one-shot `ChangePinCodeNews.ShakePinInput`; the screen counts it into the
  existing `PinCodeInput(shakeTrigger)` parameter.
- The error `Text` under the indicator stays in the composition; its visibility is animated with alpha so
  the layout does not jump.
- `collectAsStateWithLifecycle` collects the view state.

## DOD

- `./gradlew spotlessApply` has been run and all relevant tests and static analysis pass.
- `PinCodeStorageSoftwareTest` (`shared/src/commonTest`) and `PinCodeStorageDataStoreTest`
  (`app/src/androidTest`, needs an emulator or device) cover: store/verify, wrong PIN, missing PIN
  precondition, a changed PIN verifies and the old one does not, the stored KEK is unchanged by a PIN
  change, a fresh salt and verifier on every change. On Android, a private key encrypted with
  `KeyStoreEncryptor` before the change decrypts after a lock and unlock with the new PIN.
- `ChangePinCodeViewModelTest` covers the happy path, a confirmation mismatch with its shake news, a
  storage failure, and a keypad that ignores input while saving.
- `SettingsScreenTest` (`app/src/test`) asserts that the "Change PIN" row invokes `onChangePinClick`.
- Manual check (human only): on Android, with biometric unlock enabled, change the PIN, lock the app,
  unlock with biometrics, then with the new PIN, and send a transaction. Repeat the PIN part on Desktop.
