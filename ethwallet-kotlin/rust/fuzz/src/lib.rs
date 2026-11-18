use bip39::Language;
use std::sync::LazyLock;

use hmac::{Hmac, Mac};
use secp256k1::{All, PublicKey, Scalar, Secp256k1, SecretKey};
use sha2::{Digest as _, Sha256, Sha512};
use sha3::{Digest as _, Keccak256};
use unicode_normalization::UnicodeNormalization;

pub const STATUS_OK: i32 = 0;
pub const STATUS_INVALID_MNEMONIC: i32 = 1;
pub const STATUS_INVALID_PRIVATE_KEY: i32 = 2;
pub const STATUS_INVALID_ARGUMENT: i32 = 4;
pub const STATUS_INVALID_TRANSACTION: i32 = 7;

pub static SECP256K1: LazyLock<Secp256k1<All>> = LazyLock::new(Secp256k1::new);

const HARDENED_OFFSET: u32 = 1 << 31;
const PBKDF2_ROUNDS: usize = 2048;

pub const SENTINEL: u8 = 0xa5;
pub const SIGNED_TRANSACTION_CAPACITY_OVERHEAD: usize = 320;

/// Returns true when `input` is an ASCII English BIP-39 mnemonic with a valid checksum.
/// This check does not use the `bip39` parser. It uses only the `bip39` word list.
pub fn is_valid_english_mnemonic(input: &[u8]) -> bool {
    let Ok(text) = std::str::from_utf8(input) else {
        return false;
    };
    if !text.is_ascii() {
        return false;
    }
    let list = Language::English.word_list();
    let indices: Option<Vec<u16>> = text
        .split_whitespace()
        .map(|word| {
            list.iter()
                .position(|candidate| *candidate == word)
                .map(|index| index as u16)
        })
        .collect();
    let Some(indices) = indices else {
        return false;
    };
    if ![12, 15, 18, 21, 24].contains(&indices.len()) {
        return false;
    }
    let total_bits = indices.len() * 11;
    let checksum_bits = total_bits / 33;
    let entropy_bits = total_bits - checksum_bits;
    let mut bits = Vec::with_capacity(total_bits);
    for index in indices {
        for shift in (0..11).rev() {
            bits.push((index >> shift) & 1 == 1);
        }
    }
    let entropy: Vec<u8> = bits[..entropy_bits]
        .chunks(8)
        .map(|byte| {
            byte.iter()
                .fold(0_u8, |value, bit| (value << 1) | u8::from(*bit))
        })
        .collect();
    let hash = Sha256::digest(&entropy);
    (0..checksum_bits)
        .all(|bit| ((hash[bit / 8] >> (7 - bit % 8)) & 1 == 1) == bits[entropy_bits + bit])
}

/// Returns the Ethereum address of `secret_key`.
pub fn ethereum_address(secret_key: &SecretKey) -> [u8; 20] {
    let public_key = PublicKey::from_secret_key(&SECP256K1, secret_key).serialize_uncompressed();
    let hash = Keccak256::digest(&public_key[1..]);
    hash[12..]
        .try_into()
        .expect("Keccak-256 output has 32 bytes")
}

/// Returns `value` without leading zero bytes.
pub fn minimal_quantity(value: &[u8]) -> &[u8] {
    let first_nonzero = value
        .iter()
        .position(|byte| *byte != 0)
        .unwrap_or(value.len());
    &value[first_nonzero..]
}

/// Derives the private key at m/44'/60'/account'/0/address with the `secp256k1` C library.
/// It does not use `bip39` seed code or `bip32`. `mnemonic` must be valid. `account` and
/// `address` must be below 2^31.
pub fn derive_bip44_private_key(
    mnemonic: &str,
    passphrase: &str,
    account: u32,
    address: u32,
) -> [u8; 32] {
    let sentence = mnemonic.split_whitespace().collect::<Vec<_>>().join(" ");
    let salt = format!("mnemonic{}", passphrase.nfkd().collect::<String>());
    let seed = pbkdf2_hmac_sha512(sentence.as_bytes(), salt.as_bytes());
    let master = hmac_sha512(b"Bitcoin seed", &[&seed]);
    let mut key = SecretKey::from_slice(&master[..32]).expect("master key is valid");
    let mut chain_code: [u8; 32] = master[32..].try_into().expect("chain code has 32 bytes");
    for index in [
        44 | HARDENED_OFFSET,
        60 | HARDENED_OFFSET,
        account | HARDENED_OFFSET,
        0,
        address,
    ] {
        let child = if index & HARDENED_OFFSET == 0 {
            let public_key = PublicKey::from_secret_key(&SECP256K1, &key).serialize();
            hmac_sha512(&chain_code, &[&public_key, &index.to_be_bytes()])
        } else {
            hmac_sha512(
                &chain_code,
                &[&[0], &key.secret_bytes(), &index.to_be_bytes()],
            )
        };
        let tweak: [u8; 32] = child[..32].try_into().expect("tweak has 32 bytes");
        key = key
            .add_tweak(&Scalar::from_be_bytes(tweak).expect("tweak is below the curve order"))
            .expect("child key is valid");
        chain_code = child[32..].try_into().expect("chain code has 32 bytes");
    }
    key.secret_bytes()
}

fn pbkdf2_hmac_sha512(password: &[u8], salt: &[u8]) -> [u8; 64] {
    let mut block = hmac_sha512(password, &[salt, &1_u32.to_be_bytes()]);
    let mut output = block;
    for _ in 1..PBKDF2_ROUNDS {
        block = hmac_sha512(password, &[&block]);
        output
            .iter_mut()
            .zip(block)
            .for_each(|(byte, next)| *byte ^= next);
    }
    output
}

fn hmac_sha512(key: &[u8], parts: &[&[u8]]) -> [u8; 64] {
    let mut mac = Hmac::<Sha512>::new_from_slice(key).expect("HMAC accepts any key length");
    parts.iter().for_each(|part| mac.update(part));
    mac.finalize().into_bytes().into()
}
