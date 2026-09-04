# ethwallet-kotlin native support

The Android Rust wallet library is packaged for the 64-bit `arm64-v8a` and
`x86_64` ABIs only. Consuming applications must restrict their Android ABI
configuration to those two architectures; 32-bit Android devices are not
supported.

The desktop JVM library supports Apple Silicon macOS only.

## Secret handling

The library keeps mnemonic, seed, private-key, and signing buffers scoped to the
operation that needs them and zeroizes owned temporary copies where the backing
types support it. BIP-32 derivation uses `bip32` with the `k256` backend so
extended private keys are not implicitly copied and private scalars are
zeroized when their signing-key owners are dropped.

This cleanup is best effort. BIP-32 chain codes, HMAC state and output, tweak
buffers, compiler-created moves, and copies made after data crosses the native
boundary are not guaranteed to be erased. It also does not protect secrets from
a debugger, a compromised or rooted process, crash dumps, or an attacker reading
memory while an operation is running.
