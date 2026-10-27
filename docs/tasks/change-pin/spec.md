# Task

Allow an unlocked user to change their current PIN from the Settings screen.

## Scope

- `PinCodeStorage`: new `changePinCode(currentPin, newPin)` operation, implemented by
  `PinCodeStorageDataStore` (`shared/src/androidMain`) and `PinCodeStorageSoftware` (`shared/src/commonMain`,
  used by Desktop). There is no iOS source set. The test-only implementations must also be updated:
  `FakePinCodeStorage` (`shared/src/commonTest`), the anonymous `object : PinCodeStorage` in
  `app/src/test/java/me/sdi/ethwallet/flow/WalletFlowTest.kt`, and `InMemoryPinCodeStorage` in
  `app/src/test/java/me/sdi/ethwallet/flow/e2e/environment/SepoliaE2eEnvironment.kt`.
- `SettingsScreen`: a "Change PIN" row that opens the new flow through a new `onChangePinClick: () -> Unit`
  parameter, wired in `EthWalletApp` like `onBack`. `SettingsViewModel` is not changed; navigation from
  Settings is done with plain callbacks today, not view model news.
- `ChangePinCodeScreen` and `ChangePinCodeViewModel` in `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/settings/`:
  the new three-step flow, reached through a new `Destination.ChangePinCodeScreen` in
  `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/navigation/Destination.kt` and registered in `EthWalletApp`
  with the same `extraPane()` metadata as `Destination.SettingsScreen`.
- Compose string resources for the new texts in `shared/src/commonMain/composeResources/values/strings.xml`.
  Error messages shown under the indicator are plain strings in the ViewModel, as in the existing PIN view models.

The flow does not change the onboarding `SetPinCodeScreen`, the lock screen, or the PIN length (6 digits).

## Domain rules

### Key hierarchy must be preserved

The PIN envelope wraps a random master KEK. Wallet private keys (`KeyStoreEncryptor`,
`SoftwarePrivateKeyEncryptor`) and the biometric envelope (`bio_wrapped_kek`) are encrypted under that same
KEK.

- Changing the PIN re-wraps the **existing** master KEK under a key derived from the new PIN. It must not
  mint a new KEK, so `storePinCode` must not be reused for this.
- After a change, every stored private key still decrypts, and biometric unlock keeps working without
  re-enrollment.
- The master KEK, wallets, Room data, and biometric artifacts are not modified.

### `changePinCode(currentPin: String, newPin: String): Boolean`

1. Read the stored salt (`pin_kdf_salt`) and wrapped KEK (`pin_wrapped_kek`). If they are missing, return
   `false`. On Android, gate on the same `hasPinCode()` check as `verifyPinCode`, which also requires the
   `wallet_pin_code_key` alias; a missing alias would otherwise surface as a null cast, not a
   `GeneralSecurityException`.
2. Derive `pinKey` from `currentPin` and unwrap the KEK, using the same steps as `verifyPinCode`. On Android,
   that includes the AndroidKeyStore outer layer. If the unwrap fails (`GeneralSecurityException`, or
   `IllegalArgumentException` from Base64 decoding or a blob shorter than the IV, exactly the set
   `verifyPinCode` catches), return `false` and leave storage unchanged.
3. Generate a fresh random salt, derive `newPinKey` from `newPin`, and wrap the unwrapped KEK with
   `KekCipher`. On Android, wrap the result again with the existing AndroidKeyStore PIN key
   (`wallet_pin_code_key`). Do not delete or regenerate that key.
4. Write the new salt and wrapped KEK in a **single** `dataStore.edit`, so a failure or process death
   leaves either the old PIN or the new PIN fully valid, never a mix. Both platforms already use a
   `DataStore<Preferences>` (`context.preferencesDataStore` on Android, `pinArtifactsDataStore` on Desktop),
   whose `edit` is atomic. Keep the existing Base64 encoding of each platform.
5. Return `true`.

Additional rules:

- Zero `pinKey`, `newPinKey`, and the unwrapped KEK copy in `finally` blocks. The session's own KEK
  reference held by `PinSession` is not cleared or replaced: unlike `verifyPinCode`, do not call
  `pinSession.set(...)` or `userActivityManager.onAppUnlocked()`.
- Do not call `pinAttemptsGuard.bootstrap()` or `clear()`. The failure counter is managed by the
  ViewModel (see below).
- `CancellationException` is always rethrown. Unexpected exceptions other than the unwrap failures above
  propagate to the caller.

### Attempt limiting

Entering the current PIN uses the same `PinAttemptsGuard` and `PinLockPolicy` as the lock screen
(`VerifyPinCodeViewModel`). Settings must not become an unlimited PIN-guessing oracle. (On Desktop the
registered `DesktopPinAttemptsGuard` never locks; that is pre-existing and out of scope.)

- Before accepting input, read `pinAttemptsGuard.status()` in `init`. If `lockRemaining > Duration.ZERO`,
  show the countdown and block the keypad, the same way the lock screen does (poll `status()` again when
  the countdown reaches zero).
- A wrong current PIN calls `onFailedAttempt()` and shows "Incorrect PIN code" with the remaining attempts
  (`remainingInWindow`), or "Too many failed attempts" with the countdown when the result is locked.
- A correct current PIN (a successful change) calls `onSuccess()`.
- `PinLockState.Tampered` (from `status()` or `onFailedAttempt()`) follows the lock screen behavior: call
  `DataCleaner.clean()`, then emit a logged-out news item that `EthWalletApp` maps to clearing the back
  stack and adding `Destination.OnBoardingScreen`. Note `clean()` also clears `PinSession`, which can
  trigger the existing lock-screen redirect first; the onboarding navigation must tolerate a back stack
  that was already replaced.

### New PIN validation

- The new PIN must be 6 digits and confirmed by entering it a second time.
- If the confirmation does not match, show "PINs don't match. Try again." and return to the new-PIN step
  with both entries cleared. The entered current PIN is kept, so the user does not re-enter it.
- If the new PIN equals the entered current PIN, reject it as soon as its 6th digit is entered (before the
  confirmation step) with "New PIN must be different from the current PIN" and return to the new-PIN step.

## Flow

1. **Settings**: a "Change PIN" row between the biometric row and the "Lock app" row, with the
   description "Change the PIN used to unlock the app".
2. **Enter current PIN**: the keypad and `PinCodeInput` indicator, titled "Enter current PIN". The
   current PIN is collected but not checked yet. Verification and storage happen together in step 4, so
   the current PIN is unwrapped only once.

   The current PIN can still be wrong at step 4. In that case the flow returns to this step with the
   error, the attempt counted, and the new-PIN entries discarded.
3. **Enter new PIN**: titled "Enter new PIN".
4. **Confirm new PIN**: titled "Confirm new PIN". On a match, call `changePinCode`. The keypad is
   disabled while saving.
5. **Success**: emit a one-time news item; the screen shows "PIN changed" through
   `rememberToastManager().showToast(...)` and calls a callback that `EthWalletApp` maps to
   `backStack.removeLastOrNull()`.

Back or Cancel on any step returns to Settings without changes. All entered PINs are held only in the
ViewModel state and discarded when the screen leaves the back stack.

If the session locks while the flow is open (manual lock or inactivity), the existing `EthWalletApp`
lock-screen redirect clears the back stack. No PIN change is applied unless `changePinCode` already
returned `true`.

## UI

- Reuse `PinCodeInput` and the keypad from `SetPinCodeScreen` / `VerifyPinCodeScreen`. `NumberButton`
  (`ui/wallet/NumberButton.kt`) is already shared; the keypads `NumberPad` and `VerifyNumberPad` are private
  to their screens. Extract one shared keypad composable into `ui/wallet` if needed, without changing the
  existing screens' behavior.
- Show a top app bar with a back icon (`Res.drawable.arrow_back_24px`, `Res.string.back`) and the title
  "Change PIN".
- Errors appear under the indicator, as on the existing PIN screens. Lockout shows the remaining seconds
  and attempts with the existing `pin_locked_subtitle` and `pin_attempts_remaining` resources.
- Show no biometric button and no biometric offer: biometrics stay as they are.

## Implementation details

- Follow the Events/State/News pattern: `onEvent(ChangePinCodeEvent)`, `viewState: StateFlow`, and `news`
  as a `Channel` exposed as `Flow` (as in `VerifyPinCodeViewModel`).
- Constructor dependencies: `PinCodeStorage`, `PinAttemptsGuard`, `DataCleaner`. Register
  `ChangePinCodeViewModel` in `commonModule` (`shared/src/commonMain/kotlin/me/sdi/ethwallet/di/KoinModule.kt`)
  next to the other PIN view models.
- `FakePinCodeStorage.changePinCode` must behave, not stub: throw when `shouldThrowException`, return
  `false` if `currentPin != storedPin`, otherwise set `storedPin = newPin` and return `true`; add a
  `changePinCalledCount`. The two `app/src/test` implementations only need the same in-memory logic.
- Extract the duplicated derive/unwrap code inside each storage class into private helpers shared by
  `verifyPinCode` and `changePinCode`. Do not change the envelope format or KDF parameters.

## DOD

- `./gradlew spotlessApply` has been run and all relevant tests and static analysis pass.
- `PinCodeStorageSoftwareTest` (`shared/src/commonTest`) and `PinCodeStorageDataStoreTest`
  (`app/src/androidTest`, written by the agent; running it needs an emulator or device, so it is a manual
  check for the human) cover:
  - After a change, the new PIN verifies and the old PIN does not.
  - The KEK unwrapped with the new PIN is byte-equal to the KEK from before the change.
  - A private key encrypted before the change decrypts after a lock and unlock with the new PIN. On
    Android use `KeyStoreEncryptor` with an `InMemoryPinSession` (as in `KeyStoreEncryptorTest`). The
    Software test cannot reference `SoftwarePrivateKeyEncryptor` (it lives in `desktopMain`, and
    `commonTest` compiles for both targets); encrypt a sample payload with `KekCipher.encrypt` under the
    pre-change KEK and decrypt it with the KEK republished by `verifyPinCode(newPin)` instead.
  - A wrong current PIN returns `false` and leaves the stored salt and wrapped KEK unchanged.
  - The salt changes on every successful change.
- `ChangePinCodeViewModelTest` (`shared/src/commonTest/kotlin/me/sdi/ethwallet/ui/settings/`, with
  `FakePinCodeStorage`, `FakePinAttemptsGuard`, `FakeDataCleaner`) covers:
  - The happy path and its news.
  - Confirmation mismatch.
  - Same-PIN rejection.
  - A wrong current PIN that counts an attempt and returns to step 1.
  - An initial lockout state.
  - Lockout after repeated failures.
  - Tamper causing wipe and logout.
  - A keypad that ignores input while saving.
- A Robolectric Compose test `SettingsScreenTest` in `app/src/test/java/me/sdi/ethwallet/flow/` (pattern:
  `TokensListScreenTest`) asserts that clicking the "Change PIN" row invokes `onChangePinClick`. Register
  `SettingsViewModel` with inline `BiometricUnlock`/`PinSession` stand-ins in the test's Koin module, as
  `WalletFlowTest` does; `shared/src/commonTest` fakes are not visible from `app/src/test`.
  `SettingsViewModelTest` needs no change.
- Manual check (human only): on Android, with biometric unlock enabled, change the PIN, lock the app,
  unlock with biometrics, then with the new PIN, and send a transaction. Repeat the PIN part on Desktop.
