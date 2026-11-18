#![no_main]

use arbitrary::Arbitrary;
use bip39::{Language, Mnemonic};
use ethwallet_crypto::ewc_derive_private_key;
use ethwallet_crypto_fuzz::{
    SENTINEL, STATUS_INVALID_ARGUMENT, STATUS_INVALID_MNEMONIC, STATUS_OK,
    derive_bip44_private_key, is_valid_english_mnemonic,
};
use libfuzzer_sys::fuzz_target;

const HARDENED_BIT: u32 = 1 << 31;

#[derive(Arbitrary, Debug)]
enum MnemonicInput {
    Raw(Vec<u8>),
    Generated(WordCount, [u8; 32]),
}

#[derive(Arbitrary, Debug)]
enum WordCount {
    Twelve,
    Fifteen,
    Eighteen,
    TwentyOne,
    TwentyFour,
}

#[derive(Arbitrary, Debug)]
struct Input {
    mnemonic: MnemonicInput,
    passphrase: Vec<u8>,
    null_empty_passphrase: bool,
    account_index: u32,
    address_index: u32,
}

fuzz_target!(|input: Input| {
    let mnemonic = match input.mnemonic {
        MnemonicInput::Raw(bytes) => bytes,
        MnemonicInput::Generated(word_count, entropy) => {
            let entropy_length = match word_count {
                WordCount::Twelve => 16,
                WordCount::Fifteen => 20,
                WordCount::Eighteen => 24,
                WordCount::TwentyOne => 28,
                WordCount::TwentyFour => 32,
            };
            Mnemonic::from_entropy_in(Language::English, &entropy[..entropy_length])
                .expect("entropy length is valid")
                .to_string()
                .into_bytes()
        }
    };
    let passphrase_pointer = if input.passphrase.is_empty() && input.null_empty_passphrase {
        std::ptr::null()
    } else {
        input.passphrase.as_ptr()
    };
    let mut output = [SENTINEL; 32];

    // SAFETY: the mnemonic pointer is non-null and owns its bytes. The passphrase pointer owns its
    // bytes or is null with length zero. The output has 32 writable bytes.
    let status = unsafe {
        ewc_derive_private_key(
            mnemonic.as_ptr(),
            mnemonic.len(),
            passphrase_pointer,
            input.passphrase.len(),
            input.account_index,
            input.address_index,
            output.as_mut_ptr(),
        )
    };

    let arguments_are_valid = std::str::from_utf8(&input.passphrase).is_ok()
        && input.account_index & HARDENED_BIT == 0
        && input.address_index & HARDENED_BIT == 0;
    let expected = match (is_valid_english_mnemonic(&mnemonic), arguments_are_valid) {
        (true, true) => STATUS_OK,
        (true, false) => STATUS_INVALID_ARGUMENT,
        (false, _) => STATUS_INVALID_MNEMONIC,
    };
    assert_eq!(expected, status);
    if status == STATUS_OK {
        let expected_key = derive_bip44_private_key(
            std::str::from_utf8(&mnemonic).expect("a valid mnemonic is ASCII"),
            std::str::from_utf8(&input.passphrase).expect("the passphrase is UTF-8"),
            input.account_index,
            input.address_index,
        );
        assert_eq!(expected_key, output);
    } else {
        assert_eq!([SENTINEL; 32], output);
    }
});
