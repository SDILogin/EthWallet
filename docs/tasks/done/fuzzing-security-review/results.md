# Results

Run on 2026-11-18 in the Linux x86_64 Docker agent (12 cores).

- Toolchain: `rustc 1.102.0-nightly (59cbdee2e 2026-11-17)`, `cargo-fuzz 0.13.2`.
- Library toolchain for `cargo test`, `cargo clippy` and `cargo fmt`: 1.97.1.
- Commands: [ethwallet-kotlin/rust/SECURITY_REVIEW.md](../../../../ethwallet-kotlin/rust/SECURITY_REVIEW.md).

## Fuzzing

AddressSanitizer and LeakSanitizer, 600 seconds per target. `CFLAGS="-fsanitize=address"` was set, so the
libsecp256k1 C archive was also instrumented (it has `__asan_report` symbols). The first three targets ran in
parallel. The signing target ran alone after its last oracle change.

| Target | Runs | Exec/s | Coverage | Corpus | Crashes |
| --- | --- | --- | --- | --- | --- |
| `validate_mnemonic` | 13,397,200 | 22,291 | 455 | 174 | 0 |
| `derive_private_key` | 51,909 | 86 | 1332 | 200 | 0 |
| `derive_address` | 8,326,951 | 13,855 | 175 | 3 | 0 |
| `sign_eip1559_transaction` | 4,843,886 | 8,059 | 631 | 264 | 0 |

`derive_private_key` is slow because each valid mnemonic runs PBKDF2 with 2048 rounds twice: once in the library
and once in the oracle. Ten minutes is a smoke level for this target, not deep coverage.

The signing run wrote one `slow-unit` artifact: one input took 55 seconds once. Three replays of the same input took
33 to 49 ms. The stall was in the host, not in the library.

The derivation and signing oracles were checked once with an inverted assertion. Both failed at once, so the
success paths run and match the library.

## Sanitizers

- AddressSanitizer on `cargo test --lib --tests`, with the C code instrumented: 18 tests passed, no reports.
- Miri: 9 tests passed, no undefined behavior. They cover mnemonic validation and creation, BIP-39 seeds, BIP-32
  derivation and RLP encoding. The other tests reach libsecp256k1 C code, and Miri stops there with
  "unsupported operation".
- ThreadSanitizer: the tests build with `-Zbuild-std`, but they do not start. TSan reports "incompatible memory
  layout" and cannot turn off ASLR. `setarch -R` fails with "Operation not permitted" in the Docker agent.
- MemorySanitizer: not run.

## Findings

None. No change to the library was needed.

## Not verified

The checks in "Checks on other platforms" in the review packet were not run. They need macOS, iOS or an Android
device.
