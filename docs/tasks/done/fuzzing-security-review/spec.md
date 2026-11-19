# Task

Fuzz mnemonic and raw ABI inputs, run supported sanitizers, and prepare the boundary for independent review.

## Background

`ethwallet-kotlin/rust` is the Rust crypto library behind a private C ABI. The Kotlin bindings guarantee valid
pointers, lengths and output capacity. Rust validates the semantic inputs: mnemonics, passphrases, BIP-44 indices,
private keys and transaction fields. The boundary had unit and FFI contract tests but no fuzzing, no sanitizer runs
and no document for an external reviewer.

## Scope

- A cargo-fuzz crate in `ethwallet-kotlin/rust/fuzz/` with one target per semantic ABI function:
  `validate_mnemonic`, `derive_private_key`, `derive_address`, `sign_eip1559_transaction`.
- The targets call only the public `ewc_*` functions. They respect the Kotlin preconditions: non-null mnemonic
  pointer, fixed input sizes, output buffers sized as the bindings size them (32, 20, `data_length + 320`).
  `ewc_create_mnemonic` has no fuzzable input.
- Each target checks the result against an oracle, not only for crashes. The mnemonic, derivation and signing
  oracles do not call the library parser, derivation or encoding functions. The signing oracle uses the same
  `alloy-rlp` crate, but only its decoder.
- Seeds from public BIP-39 vectors and secp256k1 scalar bounds, and a BIP-39 English dictionary.
- Supported sanitizer runs on Linux: AddressSanitizer and LeakSanitizer through the fuzz targets and the test
  suite, Miri on the tests that call no C code.
- A review packet for an external reviewer: `ethwallet-kotlin/rust/SECURITY_REVIEW.md`.
- The run results of this task: `docs/tasks/done/fuzzing-security-review/results.md`.

## Out of scope

- No change to `src/lib.rs`, the C header, the JNI bridge, the Kotlin bindings or Gradle.
- No new native runtime checks.
- Fuzzing the JNI bridge or the Kotlin bindings, CI integration, the working corpus.
- Selecting and paying the external reviewer.

## Definition of done

- The four fuzz targets build and run on Linux x86_64 with nightly cargo-fuzz.
- The Linux sanitizer runs are recorded with their results.
- Checks that need macOS, iOS or an Android device are recorded as unverified with exact manual steps.
