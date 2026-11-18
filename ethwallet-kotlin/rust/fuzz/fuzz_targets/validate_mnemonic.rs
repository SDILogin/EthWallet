#![no_main]

use ethwallet_crypto::ewc_validate_mnemonic;
use ethwallet_crypto_fuzz::{STATUS_INVALID_MNEMONIC, STATUS_OK, is_valid_english_mnemonic};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|input: &[u8]| {
    let mnemonic = input.to_vec();

    // SAFETY: `mnemonic` owns `mnemonic.len()` readable bytes. An empty Vec has a non-null pointer.
    let status = unsafe { ewc_validate_mnemonic(mnemonic.as_ptr(), mnemonic.len()) };

    let expected = if is_valid_english_mnemonic(&mnemonic) {
        STATUS_OK
    } else {
        STATUS_INVALID_MNEMONIC
    };
    assert_eq!(expected, status, "mnemonic {mnemonic:?}");
});
