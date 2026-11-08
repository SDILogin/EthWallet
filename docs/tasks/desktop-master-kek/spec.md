# Task

Protect the Desktop master KEK so that a copy of the user's files cannot decrypt wallet private keys. This must be
done before Desktop persists wallets to disk.

## Background

- Desktop supports only Apple Silicon macOS.
- `PinCodeStorageSoftware` stores the random master KEK unencrypted as `master_kek` in the owner-only
  `pinArtifactsDataStore` under `~/.ethwallet`. File permissions are its only protection.
- `SoftwarePrivateKeyEncryptor` encrypts private keys with that KEK only (`KekCipher`), with no hardware layer.
- The Desktop `AppDatabase` (`DesktopDatabase.kt`) keeps wallets, including encrypted private keys, in memory only.
  Nothing the KEK can decrypt is written to disk, so a copied `~/.ethwallet` exposes no funds today.
- `~/.ethwallet` is included in Time Machine backups, cloud-synced home folders, and disk images. Once wallets are
  persisted, such a copy would contain both the encrypted private keys and the KEK that decrypts them, with no brute
  force needed.

## Goal

A copy of the Desktop data files taken without access to the user's Mac (a backup, a synced folder, a disk image)
must not let anyone decrypt wallet private keys.

## Scope

- Stop storing the master KEK unencrypted on disk. Protect it with a macOS mechanism that does not travel with
  file copies: a Keychain item that is device-only and cannot migrate, or a key held in the Secure Enclave that wraps
  the KEK.
- The PIN stays an app-lock check (`pin_hash`, `pin_hash_salt`). It is not used as key material.
- `clearPinCode()` and the logout `DataCleaner` implementations must also delete the Keychain item or the Secure
  Enclave key.
- Update the KDoc of `PinCodeStorageSoftware` and `SoftwarePrivateKeyEncryptor` to describe the new protection.
- Keep `PinCodeStorageSoftware` usable by tests that have no Keychain: inject the KEK protection behind an
  interface, with an in-memory fake in `commonTest`.
- Out of scope: persisting the Desktop wallet database. That work must depend on this task.

## Open questions

- Keychain item vs. Secure Enclave wrapping key: compare JVM access options (JNA or JNI to Security.framework, or
  an existing library), code signing and entitlement requirements, and behavior of unsigned development builds.
- Whether the Keychain or Secure Enclave key needs user presence (Touch ID or the login password), and how that
  interacts with the in-app PIN and biometric unlock.
- Migration: none is needed while wallets are not persisted on Desktop. Confirm this still holds when the task
  starts.

## Definition of done

- `~/.ethwallet` contains no unencrypted master KEK. A test asserts that the stored artifacts do not contain the
  KEK bytes.
- Unit tests for `PinCodeStorageSoftware` with the fake KEK protection: store, verify, change PIN, clear.
- Manual check on Apple Silicon macOS: set a PIN, restart the app, unlock, sign a transfer; copy `~/.ethwallet`
  to another user account or machine and confirm the KEK cannot be recovered from the copy.
- Formatting, Desktop compilation, and shared tests pass.
