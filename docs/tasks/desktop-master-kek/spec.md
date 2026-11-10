# Task

Protect the Desktop master KEK so that a copy of the user's files cannot decrypt wallet private keys. This must be
done before Desktop persists wallets to disk.

## Background

- Desktop supports only Apple Silicon macOS.
- `MasterKekStorageSoftware` stores the random master KEK unencrypted as `master_kek` in the owner-only
  `pinArtifactsDataStore` under `~/.ethwallet`. File permissions are its only protection.
- `SoftwarePrivateKeyEncryptor` encrypts private keys with that KEK only (`KekCipher`), with no hardware layer.
- Commit 90ad1ac "Persist Desktop wallet data with Room" persisted the Desktop `AppDatabase` to
  `~/.ethwallet/ethwallet_db` before this task was done. Since then a copied `~/.ethwallet` contains both the
  encrypted private keys and the plain KEK that decrypts them, and existing installs need a migration of the plain
  `master_kek`. This scope was implemented in #307 Secure Desktop private key storage and app unlock.
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
- `MasterKekStorage.clear()` and the logout `DataCleaner` implementations must also delete the Keychain item or the Secure
  Enclave key. A CryptoKit Secure Enclave key exists only as its stored `dataRepresentation` blob, so removing that
  blob is its deletion.
- Update the KDoc of `MasterKekStorageSoftware` and `SoftwarePrivateKeyEncryptor` to describe the new protection.
- Keep `MasterKekStorageSoftware` usable by tests that have no Keychain: inject the KEK protection behind an
  interface, with an in-memory fake in `commonTest`.
- Out of scope: persisting the Desktop wallet database (already done by 90ad1ac, see Background).

## Decisions

- Compared with Monero GUI: wallet2 encrypts the `.keys` file with ChaCha20 under a key derived from the wallet
  password by CryptoNight (`cn_slow_hash`). The password is the only key material, so a copied keys file can be
  attacked offline by guessing the password. A Secure Enclave key cannot be copied at all, so no offline attack
  exists, and the system sheet adds the OS-rate-limited account password or Touch ID. A wallet password on top
  would add only a brute-forceable layer and a second secret to remember, so it is not added.
- Secure Enclave wrapping key, not a Keychain item. A CryptoKit `SecureEnclave.P256.KeyAgreement.PrivateKey` is
  stored only as its `dataRepresentation` blob in the DataStore, needs no Keychain entitlement, and works for
  unsigned development builds. A device-only data-protection Keychain item needs a signed app with keychain
  entitlements, and a login-keychain item travels with home-folder backups. The Swift library is called through JNA.
- The Secure Enclave key requires user presence (`.userPresence`: Touch ID or the macOS account password).
  Unwrapping the KEK on every unlock shows the system sheet after the PIN. This stops a person at an unlocked Mac
  who could otherwise guess the PIN without an attempt limit or use the key blob from a script. Wrapping uses only
  the public key, so setting a PIN does not prompt. Desktop biometric unlock stays unsupported.
- The PIN stays the shared app lock (extend, not replace). With the system sheet on every unlock, the Desktop PIN
  adds no protection against an attacker the sheet does not already stop. Replacing it on Desktop needs
  Desktop-specific PIN screens and navigation and is left to a separate decision.
- Migration: needed, because wallets were already persisted when the task started (see Background). The plain
  `master_kek` is wrapped and removed on the first read. The KEK is not rotated, so a copy taken before the
  migration still decrypts the private keys.
- Memory: the KEK stays in process memory while the app is unlocked (`PinSession`). The Swift
  `Data` and `SymmetricKey` copies and the JNA transfer buffers are not zeroed; Kotlin keeps its existing zeroing of
  decrypted private keys. Cancelling the unlock coroutine does not dismiss the system sheet.

## Definition of done

- `~/.ethwallet` contains no unencrypted master KEK. A test asserts that the stored artifacts do not contain the
  KEK bytes.
- Unit tests for `MasterKekStorageSoftware` with the fake KEK protection: create, read, clear.
- Manual check on Apple Silicon macOS: set a PIN, restart the app, unlock, sign a transfer; copy `~/.ethwallet`
  to another user account or machine and confirm the KEK cannot be recovered from the copy.
- Formatting, Desktop compilation, and shared tests pass.
