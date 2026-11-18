# Results

Run on 2026-11-18 in the Linux x86_64 Docker agent (12 cores).

- Toolchain: `rustc 1.102.0-nightly (59cbdee2e 2026-11-17)`, `cargo-fuzz 0.13.2`.
- Library toolchain for `cargo test`, `cargo clippy` and `cargo fmt`: 1.97.1.
- Commands: [ethwallet-kotlin/rust/SECURITY_REVIEW.md](../../../../ethwallet-kotlin/rust/SECURITY_REVIEW.md).

## Fuzzing (AddressSanitizer and LeakSanitizer, 600 seconds each, four targets in parallel)

| Target | Runs | Exec/s | Coverage | Corpus | Crashes |
| --- | --- | --- | --- | --- | --- |
| `validate_mnemonic` | 9,572,508 | 15,927 | 450 | 130 | 0 |
| `derive_private_key` | 88,751 | 147 | 1182 | 165 | 0 |
| `derive_address` | 7,734,480 | 12,869 | 175 | 3 | 0 |
| `sign_eip1559_transaction` | 3,807,521 | 6,335 | 627 | 207 | 0 |

`derive_private_key` is slow because each valid mnemonic runs PBKDF2 with 2048 rounds. Ten minutes is a smoke
level for this target, not deep coverage.

## Sanitizers

- AddressSanitizer on `cargo test --lib --tests`: 18 tests passed, no reports.
- Miri: 6 tests passed, no undefined behavior. The other tests reach the `secp256k1` C code, and Miri stops
  there with "unsupported operation".

## Findings

None. No change to the library was needed.

## Not verified

The checks in "Checks on other platforms" in the review packet were not run. They need macOS, iOS or an Android
device.
