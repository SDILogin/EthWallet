#![no_main]

use ethwallet_crypto::ewc_derive_address;
use ethwallet_crypto_fuzz::{SENTINEL, STATUS_INVALID_PRIVATE_KEY, STATUS_OK, ethereum_address};
use libfuzzer_sys::fuzz_target;
use secp256k1::SecretKey;

fuzz_target!(|private_key: [u8; 32]| {
    let mut output = [SENTINEL; 20];

    // SAFETY: the input has 32 readable bytes and the output has 20 writable bytes.
    let status = unsafe { ewc_derive_address(private_key.as_ptr(), output.as_mut_ptr()) };

    match SecretKey::from_slice(&private_key) {
        Ok(secret_key) => {
            assert_eq!(STATUS_OK, status);
            assert_eq!(ethereum_address(&secret_key), output);
        }
        Err(_) => {
            assert_eq!(STATUS_INVALID_PRIVATE_KEY, status);
            assert_eq!([SENTINEL; 20], output);
        }
    }
});
