# Results

Run on 2026-11-18 in the Linux x86_64 Docker agent (12 cores).

- Toolchain: `rustc 1.102.0-nightly (59cbdee2e 2026-11-17)`, `cargo-fuzz 0.13.2`.
- Library toolchain for `cargo test`, `cargo clippy` and `cargo fmt`: 1.97.1.
- Commands: [ethwallet-kotlin/rust/SECURITY_REVIEW.md](../../../../ethwallet-kotlin/rust/SECURITY_REVIEW.md).

## Fuzzing (AddressSanitizer and LeakSanitizer, 600 seconds each, four targets in parallel)

| Target | Runs | Exec/s | Coverage | Corpus | Crashes |
| --- | --- | --- | --- | --- | --- |
| `validate_mnemonic` | 16,435,245 | 27,346 | 455 | 197 | 0 |
| `derive_private_key` | 59,586 | 99 | 1398 | 209 | 0 |
| `derive_address` | 14,220,156 | 23,660 | 175 | 3 | 0 |
| `sign_eip1559_transaction` | 6,742,402 | 11,218 | 631 | 262 | 0 |

`derive_private_key` is slow because each valid mnemonic runs PBKDF2 with 2048 rounds twice: once in the library
and once in the oracle. Ten minutes is a smoke level for this target, not deep coverage.

The derivation and signing oracles were checked once with an inverted assertion. Both failed at once, so the
success paths run and match the library.

## Sanitizers

- AddressSanitizer on `cargo test --lib --tests`: 18 tests passed, no reports.
- Miri: 9 tests passed, no undefined behavior. They cover mnemonic validation and creation, BIP-39 seeds, BIP-32
  derivation and RLP encoding. The other tests reach libsecp256k1 C code, and Miri stops there with
  "unsupported operation".

## Findings

None. No change to the library was needed.

## Not verified

The checks in "Checks on other platforms" in the review packet were not run. They need macOS, iOS or an Android
device.
