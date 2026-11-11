# Task

#307 Secure Desktop private key storage and app unlock. Private keys on Desktop (Apple Silicon macOS) must be
stored safely and must not be easy to decrypt, also from a copy of the app data files. This task includes the
scope of #303 Protect Desktop master KEK before persisting wallets (docs/tasks/desktop-master-kek/spec.md).

## Background

- Desktop persisted wallets (encrypted private keys) to `~/.ethwallet/ethwallet_db` while the master KEK sat next to
  them as plain Base64, so a copy of `~/.ethwallet` decrypted every wallet.
- Desktop used the Android PIN flow: a 6-digit PIN checked against a PBKDF2 hash, with no attempt limit and no part
  in the key protection.
- Monero GUI (wallet2) encrypts the `.keys` file with ChaCha20 under a key derived from the wallet password by
  CryptoNight; the password is the only key material, so a copied keys file can be attacked offline.

## Scope

- Desktop has no PIN. Onboarding asks for a wallet password and its confirmation; the lock screen asks for the
  password; "Forgot password?" resets the local data. Android keeps its PIN flow.
- Password strength (OWASP): shorter than 15 characters, or a zxcvbn score below 3, shows a weak-password warning.
  A weak password never blocks setup; only an empty password or a mismatched confirmation does, plus the short wait
  while the strength of a password of 15 or more characters is rated.
- Key protection: KEK -> AES-256-GCM under Argon2id(NFKC password, random 16-byte salt; m=64 MiB, t=3, p=1) ->
  Secure Enclave P-256 key wrap (CryptoKit through JNA, no user presence). Stored: Secure Enclave key handle, salt,
  wrapped KEK.
- Out of scope: change password, Touch ID unlock, breached-password lookups, attempt limits, Android.

## Decisions

- Password inside, Secure Enclave outside: a copy taken off this Mac cannot be unwrapped, so it cannot even be tested
  against a password guess. On this Mac, a process running as the user can remove the Secure Enclave layer without a
  prompt and then guess offline; there the protection is the password strength and the Argon2id cost. A zxcvbn
  score 3 password needs about 10^8-10^10 guesses; at roughly 10^3-10^4 Argon2id guesses per second per GPU (m=64
  MiB) that is hours to months for one GPU, less with more GPUs. Score 4 needs more than 10^10 guesses.
- No `.userPresence` on the Secure Enclave key: the password is the user secret and a second prompt per unlock was
  rejected. Same-user malware therefore reaches the password-protected blob without a Touch ID or account-password
  prompt.
- Argon2id over scrypt, bcrypt and PBKDF2: OWASP's first choice; bcrypt is a password hash, not a key derivation
  function, and caps input at 72 bytes.
- Argon2 (Bouncy Castle) and zxcvbn are Desktop-only dependencies behind `PasswordKeyDerivation` and
  `PasswordStrengthChecker`, so Android does not ship them.
- Onboarding order: welcome -> wallet password -> wallets list, like a password manager's master password: one
  password protects every wallet in the app.
- No migration: the app is not released.
- Memory: the KEK stays in process memory while unlocked (`PinSession`). Password bytes and the derived key are zeroed
  after use; the password `String` in view state, the Swift `Data` copies and the JNA buffers cannot be zeroed.

## Definition of done

- Unit tests: `PasswordKekStorageSoftware` (round trip, wrong password, Unicode normalization, salt per create, no KEK
  or password on disk), `PasswordInteractor`, set-password and unlock view models, Settings,
  Koin resolution of the Desktop screens without PIN bindings.
- Desktop tests: `DesktopAppTest` (set password, lock, wrong password, right password, fake Secure Enclave), the
  zxcvbn strength checker, and an Argon2id reference test vector.
- Manual check on Apple Silicon macOS: onboarding, unlock, a Sepolia transfer; a copy of `~/.ethwallet` on another
  Mac cannot be unlocked; record whether a copy for another user account on the same Mac can be unlocked with the
  password (the Secure Enclave key may be usable there).
