# ethwallet_crypto security review packet

This packet is for an independent reviewer of the native crypto boundary. It describes the boundary, the
preconditions, the semantic checks, the fuzz targets and the sanitizer runs.

The results of the first Linux run are in `docs/tasks/done/fuzzing-security-review/results.md`.

## Scope

In scope:

- `ethwallet-kotlin/rust/src/lib.rs`: the Rust library, the private C ABI and the JNI bridge.
- `ethwallet-kotlin/rust/include/ethwallet_crypto.h` and `ethwallet-kotlin/rust/interop/ethwallet_crypto.def`.
- The Kotlin bindings that call the ABI:
  - `ethwallet-kotlin/src/commonMain/kotlin/me/sdi/ethwallet/kotlin/NativeCrypto.kt`
  - `ethwallet-kotlin/src/commonMain/kotlin/me/sdi/ethwallet/kotlin/NativeCryptoSupport.kt`
  - `ethwallet-kotlin/src/jvmMain/kotlin/me/sdi/ethwallet/kotlin/NativeCrypto.jvm.kt`
  - `ethwallet-kotlin/src/androidMain/kotlin/me/sdi/ethwallet/kotlin/NativeCrypto.android.kt`
  - `ethwallet-kotlin/src/iosMain/kotlin/me/sdi/ethwallet/kotlin/NativeCrypto.ios.kt`
- The input normalization in `ethwallet-kotlin/src/commonMain/kotlin/me/sdi/ethwallet/kotlin/Mnemonic.kt`.

Out of scope: app UI, storage of secrets in the app, network code, the third-party crates.

## Boundary

```
Kotlin API -> NativeCrypto (JVM/Android: JNI, iOS: cinterop) -> C ABI ewc_* -> Rust
```

- Only the Kotlin bindings call the C ABI. The ABI is not a public API.
- Kotlin owns non-null pointers, fixed input sizes and output capacity.
- Rust owns semantic validation of all externally sourced values.
- Rust does not check states that the bindings exclude. This is a design rule. Do not report a missing null or
  length check as a defect unless a binding can reach that state.

## ABI functions

| Function | Preconditions owned by Kotlin | Semantic checks owned by Rust | Status codes |
| --- | --- | --- | --- |
| `ewc_abi_version` | none | none | returns 1 |
| `ewc_create_mnemonic` | 107 writable output bytes, 1 writable `size_t` | entropy source | OK, ENTROPY_FAILURE |
| `ewc_validate_mnemonic` | non-null input with `length` readable bytes | UTF-8, ASCII, BIP-39 English words, word count, checksum | OK, INVALID_MNEMONIC |
| `ewc_derive_private_key` | non-null mnemonic, passphrase readable or NULL with length 0, 32 writable output bytes | mnemonic as above, passphrase UTF-8, account and address index below 2^31 | OK, INVALID_MNEMONIC, INVALID_ARGUMENT |
| `ewc_derive_address` | 32 readable bytes, 20 writable bytes | secp256k1 scalar range | OK, INVALID_PRIVATE_KEY, ENTROPY_FAILURE |
| `ewc_sign_eip1559_transaction` | 32-byte key, 32-byte quantities, 20-byte destination, data readable or NULL with length 0, output of `data_length + 320` bytes, 1 writable `size_t` | chain ID not zero, priority fee not above max fee, secp256k1 scalar range | OK, INVALID_TRANSACTION, INVALID_PRIVATE_KEY, ENTROPY_FAILURE |

Each function except `ewc_abi_version` can also return INTERNAL_PANIC. ENTROPY_FAILURE in address derivation and
signing comes from the creation of the randomized secp256k1 context. A `OnceLock` keeps the first result, so after
one failure every later call fails.

Status order: mnemonic, then passphrase, then indices. For signing: transaction fields, then ENTROPY_FAILURE, then
private key. For address derivation: private key, then ENTROPY_FAILURE.

Two elliptic-curve implementations are in use:

- BIP-32 key derivation uses the `bip32` crate with the pure Rust `k256` backend.
- Address derivation and signing use the `secp256k1` crate, which wraps the libsecp256k1 C library.

Rules that the review can check:

- Each `ewc_*` function writes its outputs only on success.
- When an `ewc_*` call fails, the JNI function copies a zeroed native buffer to the Java output array. When the
  JNI code fails before the call, it returns without a write.
- Each `ewc_*` function except `ewc_abi_version` catches panics and returns INTERNAL_PANIC (`ffi_status`). The JNI
  code outside these calls does not catch panics. The release profile uses `panic = "unwind"` and
  `overflow-checks = true`.
- Owned secret copies use `Zeroizing`. The limits of this cleanup are in
  [the README "Secret handling" section](../README.md#secret-handling).

Pointer facts of the bindings:

- iOS returns INVALID_ARGUMENT for an empty mnemonic before it calls Rust.
- JNI copies each Java array into an owned Rust `Vec`. An empty array gives a non-null dangling pointer with
  length 0.
- iOS passes NULL for an empty passphrase or empty calldata. JNI passes a non-null pointer.
- The signing output capacity is `data.size + NATIVE_SIGNED_TRANSACTION_CAPACITY_OVERHEAD` (320). The largest
  encoding is about `data_length + 282` bytes.

## Fuzz targets

The fuzz crate is `ethwallet-kotlin/rust/fuzz`. It is a separate Cargo workspace. Its `Cargo.lock` uses the same
versions as the library `Cargo.lock` for every shared crate. The targets call only the public `ewc_*` functions.
They use the buffer sizes and pointer forms of the bindings.

| Target | Input | Oracle |
| --- | --- | --- |
| `validate_mnemonic` | raw bytes | An own BIP-39 check: ASCII, English word list, word count, SHA-256 checksum. The status must match. |
| `derive_private_key` | raw bytes or a mnemonic from random entropy (12 to 24 words), passphrase bytes, NULL or non-null empty passphrase, any `u32` indices | The status must follow the order above. The expected status comes from the own BIP-39 check of `validate_mnemonic`. On failure the output must not change. On success the key must equal an own BIP-39 seed (PBKDF2-HMAC-SHA512, NFKD passphrase) and BIP-32 derivation on libsecp256k1. This compares `k256` with libsecp256k1. |
| `derive_address` | 32 bytes | OK if and only if `secp256k1` accepts the scalar. The address must equal Keccak-256 of the public key, last 20 bytes. On failure the output must not change. The oracle uses the same crates as the library, so it checks the ABI handling only. |
| `sign_eip1559_transaction` | key, chain ID, all fields, calldata, NULL or non-null empty calldata | The status must follow the order above. On failure the outputs must not change. On success: the length fits the capacity, the type byte is `0x02`, the RLP list has the input fields with minimal quantities, `y_parity` is 0 or 1, `s` is low, and the recovered signer is the key address. The oracle decodes with the `alloy-rlp` decoder and writes the signing list header by hand. |

Seeds:

- `fuzz/seeds/validate_mnemonic`: public BIP-39 vectors and near-valid mnemonics.
- `fuzz/seeds/derive_address`: the scalars 0, 1, n - 1, n and 2^256 - 1.
- `fuzz/bip39_english.dict`: the BIP-39 English word list as a libFuzzer dictionary. The mnemonic targets use it.

`libfuzzer-sys` aborts on a panic before `ffi_status` can catch it. So a library panic is a fuzz crash. It is
never an INTERNAL_PANIC status in a fuzz run.

## Commands

Fuzzing needs a nightly toolchain and `cargo install cargo-fuzz`. Each command in `fuzz/` needs `+nightly`,
because `rust-toolchain.toml` pins the stable library toolchain. `CFLAGS` makes the `cc` crate build the
libsecp256k1 C code with AddressSanitizer too.

```bash
cd ethwallet-kotlin/rust/fuzz
export CFLAGS="-fsanitize=address"
mkdir -p corpus/validate_mnemonic corpus/derive_private_key corpus/derive_address corpus/sign_eip1559_transaction
cargo +nightly fuzz run validate_mnemonic corpus/validate_mnemonic seeds/validate_mnemonic -- \
  -dict=bip39_english.dict -max_total_time=600
cargo +nightly fuzz run derive_private_key corpus/derive_private_key -- \
  -dict=bip39_english.dict -max_len=512 -max_total_time=600
cargo +nightly fuzz run derive_address corpus/derive_address seeds/derive_address -- -max_total_time=600
cargo +nightly fuzz run sign_eip1559_transaction corpus/sign_eip1559_transaction -- -max_total_time=600
```

After an ABI change, check that the targets still build: `cargo +nightly fuzz build` and
`cargo clippy --all-targets -- -D warnings` in `fuzz/`.

`cargo fuzz` builds the Rust code with AddressSanitizer. LeakSanitizer runs with it on Linux.

Library tests with AddressSanitizer (Linux x86_64; on Apple Silicon macOS use `aarch64-apple-darwin`):

```bash
cd ethwallet-kotlin/rust
CFLAGS="-fsanitize=address" RUSTFLAGS="-Zsanitizer=address" CARGO_TARGET_DIR=target/asan \
  cargo +nightly test --target x86_64-unknown-linux-gnu --lib --tests
```

Library tests with Miri (needs `rustup +nightly component add miri rust-src`):

```bash
cd ethwallet-kotlin/rust
cargo +nightly miri test --lib -- encodes_zero_quantities rejects_invalid_eip1559 official_bip39_passphrase_vectors
cargo +nightly miri test --test ffi_contract -- abi_version_is_one validates_the_canonical_english_mnemonic \
  created_mnemonic_is_english_and_validates invalid_utf8_passphrase_does_not_write_output \
  rejects_bip32_indices_with_the_hardened_bit_set_without_writing_output \
  passphrases_use_utf8_nfkd_and_preserve_case_whitespace_and_nul
```

PBKDF2 is slow under Miri. The derivation tests take from 10 to 75 minutes.

## Sanitizer limits

- With the `CFLAGS` above, AddressSanitizer also instruments the libsecp256k1 C code. Without it, the C code is
  not instrumented.
- Miri cannot run C code. The tests that reach libsecp256k1 (address derivation and signing) stop with
  "unsupported operation". Miri covers only the tests above.
- The JNI bridge compiles only for Android and macOS. Linux runs do not cover it.
- MemorySanitizer was not run. It needs `-Zbuild-std` and all C code built with MemorySanitizer. This task did not
  set that up.
- ThreadSanitizer builds with `-Zbuild-std`, but the Docker agent cannot run it. TSan needs ASLR off, and the
  sandbox blocks `personality()`. The only shared state is the `OnceLock` secp256k1 context.
- HWASan for Android is not set up in this project.
- The signing target uses the default `-max_len` of 4096 bytes. Calldata of 65536 bytes or more, which uses a
  longer RLP length header, is not fuzzed.
- The derivation oracle uses `expect` on BIP-32 steps that fail with a probability of about 2^-127. Such a crash
  would be an oracle limit, not a library defect.

## Checks on other platforms

These checks need macOS, iOS or an Android device. Run them before the review closes.

1. macOS on Apple Silicon: run the four fuzz commands above. This covers the `aarch64-apple-darwin` build.
2. macOS on Apple Silicon: `./gradlew :ethwallet-kotlin:jvmTest`. This runs the JNI bridge through the JVM bindings.
3. macOS with Xcode: `./gradlew :ethwallet-kotlin:iosSimulatorArm64Test`. This runs the cinterop bindings.
4. macOS on Apple Silicon: `./gradlew :ethwallet-kotlin:testAndroidHostTest`. This runs the Android JNI bridge on
   the host.
5. Android arm64 device or emulator connected with `adb`: `./gradlew :ethwallet-kotlin:connectedAndroidDeviceTest`.

## Questions for the reviewer

1. Can any binding reach a state that the ABI excludes: a NULL mnemonic, a short fixed buffer or a small signing
   output?
2. Is the signing capacity overhead of 320 bytes always enough?
3. Is `catch_unwind` in `ffi_status` enough for each ABI function and each JNI function? Can a panic cross the
   boundary on any target?
4. Does each failure path keep the outputs unchanged? Do the JNI functions copy partial secret data to Java on
   failure?
5. Are the documented zeroization limits acceptable for this wallet? Which copies stay in memory?
6. Is the secp256k1 context randomization enough? Is a low-s signature always produced?
7. Does BIP-39 parsing accept only the inputs that the Kotlin normalization can produce, and is a wider set a risk?
