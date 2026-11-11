# Task

Allow an unlocked user to change their PIN from the Settings screen.

Desktop no longer uses the PIN: since #307 Secure Desktop private key storage and app unlock it uses a wallet
password (docs/tasks/desktop-secure-key-storage/spec.md). The Desktop parts below describe the earlier design.

## Scope

- `PinCodeStorage`: the PIN becomes an app-lock check and no longer takes part in encrypting the master
  KEK. It stores only the PIN hash and salt (`storePinCode`, `verifyPinCode`, `changePinCode`, `hasPinCode`,
  `clearPinCode`). Implemented by `PinCodeStorageDataStore` (`shared/src/androidMain`) and
  `PinCodeStorageSoftware` (`shared/src/commonMain`, used by Desktop). There is no iOS source set.
- `MasterKekStorage`: persists the master KEK (`create`, `read`, `clear`). Implemented by
  `MasterKekStorageAndroidKeyStore` (`shared/src/androidMain`) and `MasterKekStorageSoftware`
  (`shared/src/commonMain`, used by Desktop).
- `PinInteractor` (`shared/src/commonMain/kotlin/me/sdi/ethwallet/core/security/`): the
  PIN business logic on top of both storages, `PinAttemptsGuard`, `PinSession` and `UserActivityManager`.
  `SetPinCodeViewModel`, `VerifyPinCodeViewModel`, `ChangePinCodeViewModel` and `SplashScreen` use it instead of
  `PinCodeStorage`.
- Test-only implementations: `FakePinCodeStorage` and `FakeMasterKekStorage` (`shared/src/commonTest`), the
  anonymous storages in `app/src/test/java/me/sdi/ethwallet/flow/WalletFlowTest.kt`, and the hardware-only
  `InMemoryMasterKekStorage` in `app/src/test/java/me/sdi/ethwallet/flow/e2e/environment/SepoliaE2eEnvironment.kt`
  (the e2e uses the real `PinCodeStorageDataStore`).
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

- **Android** (`MasterKekStorageAndroidKeyStore`): the KEK is encrypted only by the AndroidKeyStore AES-GCM key
  `wallet_pin_code_key` and stored as `keystore_wrapped_master_kek` in the same DataStore as the PIN hash.
- **Desktop** (`MasterKekStorageSoftware`): the KEK is stored as Base64 (`master_kek`) in the owner-only
  `pinArtifactsDataStore`. File permissions are its only protection.
- **PIN hash** (both platforms): `pin_hash` is PBKDF2WithHmacSHA256(PIN, `pin_hash_salt`) with
  the existing parameters (200 000 iterations, 256-bit output, 16-byte random salt). It is compared with
  `MessageDigest.isEqual`.
- `hasPinCode()` checks only for the PIN hash. `PinInteractor.setPin` writes the KEK before the PIN hash, so a
  stored PIN hash implies a stored KEK.

There is no migration. The preference keys of the old PIN-wrapped format (`pin_kdf_salt`,
`pin_wrapped_kek`) are not read, so an old install reads as "no PIN set". `DataCleanerDataStore` and
`DataCleanerPinArtifacts` clear their whole stores and `DataCleanerAndroidKeyStore` deletes every alias,
so logout also removes the new keys.

## `PinInteractor`

### `setPin(pin)` (onboarding)

`pinAttemptsGuard.bootstrap()`, `masterKekStorage.create()`, `pinCodeStorage.storePinCode(pin)`,
`pinSession.set(kek)`, `userActivityManager.onAppUnlocked()`. Both platforms now start the inactivity timer on
onboarding.

### `unlock(pin): Boolean`

- Precondition: a PIN is stored (the lock screen is shown only after the storage check); otherwise
  `checkNotNull` throws `IllegalStateException`.
- `pinCodeStorage.verifyPinCode(pin)` derives the PIN hash from `pin` and the stored salt. On a mismatch return
  `false`.
- On a match `masterKekStorage.read()` loads the KEK (Android: decrypt with the keystore key), then
  `pinSession.set(kek)` and `userActivityManager.onAppUnlocked()`, return `true`.
- A failure to load the KEK after a matching PIN is not a wrong PIN; it propagates as an exception and is
  handled by `VerifyPinCodeViewModel` as "Something went wrong", not as a failed attempt.

### `changePin(pin)`

- Calls only `pinCodeStorage.changePinCode(pin)`.
- Precondition: a PIN is stored (Settings is reachable only while unlocked). It is not re-checked.
- Generate a fresh salt, derive the PIN hash for `pin`, and write both in a **single**
  `dataStore.edit`, so process death leaves either the old or the new PIN valid.
- The KEK, wallet keys, biometric artifacts, `PinSession` and `PinAttemptsGuard` are not touched.

## Flow

1. **Settings**: a "Change PIN" row between the biometric row and the "Lock app" row.
2. **Enter new PIN**: titled "Enter new PIN". The current PIN is not asked for: the user already unlocked
   the app to reach Settings.
3. **Confirm new PIN**: titled "Confirm new PIN". On a mismatch, show "PINs don't match. Try again.",
   clear both entries, return to step 2 and shake the indicator. On a match call `PinInteractor.changePin`; the
   keypad is disabled while saving, so the ViewModel does not guard against input while saving.
4. **Success**: emit `ChangePinCodeNews.PinChanged`; the screen shows "PIN changed" through
   `rememberToastManager().showToast(...)` and pops back to Settings.

If `changePin` throws, the flow restarts at step 2 with "Something went wrong" and a shake. Back on any
step returns to Settings without changes. If the session locks while the flow is open, the existing
`EthWalletApp` lock-screen redirect clears the back stack.

## Implementation details

- Events/State/News: `onEvent(ChangePinCodeEvent)`, `viewState: StateFlow<ChangePinCodeViewState>`, `news`
  as a `Channel` exposed as `Flow`.
- `ChangePinCodeViewModel` depends only on `PinInteractor` and is registered in `commonModule`.
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
  precondition, a changed PIN verifies and the old one does not, a fresh salt and PIN hash on every change.
- `MasterKekStorageSoftwareTest` and `MasterKekStorageAndroidKeyStoreTest` cover create/read/clear. On Android,
  a private key encrypted with `KeyStoreEncryptor` before the change decrypts after a lock and unlock with the
  new PIN.
- `PinInteractorTest` covers onboarding, unlock with the right and a wrong PIN, and a PIN change that keeps the
  master KEK.
- `ChangePinCodeViewModelTest` covers the happy path, a confirmation mismatch with its shake news, and a
  storage failure.
- `SettingsScreenTest` (`app/src/test`) asserts that the "Change PIN" row invokes `onChangePinClick`.
- Manual check (human only): on Android, with biometric unlock enabled, change the PIN, lock the app,
  unlock with biometrics, then with the new PIN, and send a transaction. Repeat the PIN part on Desktop.
