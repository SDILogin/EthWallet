# Results

Run in the Linux x86_64 Docker agent (12 cores).

- Fuzzing, MemorySanitizer and the AddressSanitizer tests: 2026-11-19, `rustc 1.102.0-nightly (65ce8611f 2026-11-18)`,
  `cargo-fuzz 0.13.2`.
- Miri and ThreadSanitizer: 2026-11-18, `rustc 1.102.0-nightly (59cbdee2e 2026-11-17)`. `src/lib.rs` did not change
  after these runs.
- Library toolchain for `cargo test`, `cargo clippy` and `cargo fmt`: 1.97.1.
- Commands: [ethwallet-kotlin/rust/SECURITY_REVIEW.md](../../../../ethwallet-kotlin/rust/SECURITY_REVIEW.md).

## Fuzzing

AddressSanitizer and LeakSanitizer, 600 seconds per target. `CFLAGS="-fsanitize=address"` was set, so the
libsecp256k1 C archive was also instrumented. Each run started from the corpus of an earlier run on 2026-11-18.
The four targets ran in parallel, together with the MemorySanitizer run and the AddressSanitizer tests.
`derive_address` ran again alone after its input moved to a heap buffer. The table shows that run.

| Target | Runs | Exec/s | Coverage | Corpus | Crashes |
| --- | --- | --- | --- | --- | --- |
| `validate_mnemonic` | 8,350,830 | 13,894 | 455 | 138 | 0 |
| `derive_private_key` | 45,681 | 76 | 1402 | 190 | 0 |
| `derive_address` | 9,393,146 | 15,629 | 176 | 3 | 0 |
| `sign_eip1559_transaction` | 3,674,426 | 6,113 | 638 | 208 | 0 |

No run wrote an artifact.

`derive_private_key` is slow because each valid mnemonic runs PBKDF2 with 2048 rounds twice: once in the library
and once in the oracle. Ten minutes is a smoke level for this target, not deep coverage.

The derivation and signing oracles were checked once with an inverted assertion on 2026-11-18. Both failed at once,
so the success paths run and match the library.

## Sanitizers

- AddressSanitizer on `cargo test --lib --tests`, with the C code instrumented: 18 tests passed, no reports.
- MemorySanitizer with `-Zbuild-std` on `validate_mnemonic`, 600 seconds: 4,513,512 runs, 7,510 exec/s,
  coverage 394, no reports. The other targets reach libsecp256k1, and gcc cannot build it with MemorySanitizer.
- Miri: 9 tests passed, no undefined behavior. They cover mnemonic validation and creation, BIP-39 seeds, BIP-32
  derivation and RLP encoding. The other tests reach libsecp256k1 C code, and Miri stops there with
  "unsupported operation".
- ThreadSanitizer: the tests build with `-Zbuild-std`, but they do not start. TSan reports "incompatible memory
  layout" and cannot turn off ASLR. `setarch -R` fails with "Operation not permitted" in the Docker agent.

## Findings

No library defect. No change to the library was needed.

Observed difference, not changed: for an empty mnemonic, the iOS binding returns INVALID_ARGUMENT and the JNI
bridge returns INVALID_MNEMONIC. `Mnemonic.parse` rejects an empty phrase before the native call, so the public API
does not show the difference. The review packet asks the reviewer about it.

## Not verified

The checks in "Checks on other platforms" in the review packet were not run. They need macOS, iOS or an Android
device.
