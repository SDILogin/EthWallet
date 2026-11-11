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
  encrypted private keys and the plain KEK that decrypts them.
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
- Logout must also delete the Keychain item or the Secure Enclave key.
- Keep the storage usable by tests that have no Keychain: inject the KEK protection behind an interface, with an
  in-memory fake in `commonTest`.

## Decisions

Implemented together with #307 Secure Desktop private key storage and app unlock; the password flow, threat boundary
and research are in docs/tasks/desktop-secure-key-storage/spec.md.

- Secure Enclave wrapping key, not a Keychain item: a CryptoKit `SecureEnclave.P256.KeyAgreement.PrivateKey` is stored
  only as its `dataRepresentation` blob in the DataStore, needs no Keychain entitlement, and works for unsigned
  development builds. A device-only data-protection Keychain item needs a signed app with keychain entitlements, and a
  login-keychain item travels with home-folder backups. The Swift library is called through JNA
  (`MasterKekProtectionSecureEnclave`).
- `PasswordKekStorageSoftware` stores the Secure Enclave key handle, the password salt and the wrapped KEK. The handle
  is the only form of the Secure Enclave key, so logout (`DataCleanerPinArtifacts`, which clears the whole store)
  deletes the key.
- No migration: the app is not released, so pre-release builds are not migrated. A Desktop install that stored the
  plain `master_kek` must reset and re-import its wallets.

## Definition of done

- `~/.ethwallet` contains no unencrypted master KEK. A test asserts that the stored artifacts do not contain the
  KEK bytes.
- Unit tests for the KEK storage with the fake protection.
- Manual check on Apple Silicon macOS: copy `~/.ethwallet` to another machine and confirm the KEK cannot be
  recovered from the copy; record the result for another user account on the same Mac.
- Formatting, Desktop compilation, and shared tests pass.
