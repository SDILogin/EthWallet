#![forbid(unsafe_op_in_unsafe_fn)]

use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    ptr, slice,
    sync::OnceLock,
};

use alloy_rlp::{Encodable, Header};
use bip32::{ChildNumber, DerivationPath, XPrv};
use bip39::{Language, Mnemonic};
use secp256k1::{All, Message, PublicKey, Secp256k1, SecretKey};
use sha3::{Digest, Keccak256};
use unicode_normalization::UnicodeNormalization;
use zeroize::Zeroizing;

const ABI_VERSION: u32 = 1;
const PRIVATE_KEY_LENGTH: usize = 32;
const ADDRESS_LENGTH: usize = 20;
const ENTROPY_LENGTH: usize = 16;
const MAX_12_WORD_MNEMONIC_LENGTH: usize = 107;

static SECP256K1_CONTEXT: OnceLock<Result<Secp256k1<All>, Status>> = OnceLock::new();

#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Status {
    Ok = 0,
    InvalidMnemonic = 1,
    InvalidPrivateKey = 2,
    InvalidArgument = 4,
    EntropyFailure = 5,
    InternalPanic = 6,
    InvalidTransaction = 7,
}

impl Status {
    const fn code(self) -> i32 {
        self as i32
    }
}

/// Returns the version of the fixed, buffer-only C ABI.
#[unsafe(no_mangle)]
pub extern "C" fn ewc_abi_version() -> u32 {
    ABI_VERSION
}

/// Creates a twelve-word English BIP-39 mnemonic in the caller-owned buffer.
///
/// # Safety
/// `output` must reference a writable buffer of `MAX_12_WORD_MNEMONIC_LENGTH` bytes and `length`
/// must reference one writable `usize`. The two pointers must remain valid for the duration of
/// this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_create_mnemonic(output: *mut u8, length: *mut usize) -> i32 {
    ffi_status(|| {
        let mut entropy = Zeroizing::new([0_u8; ENTROPY_LENGTH]);
        getrandom::fill(entropy.as_mut()).map_err(|_| Status::EntropyFailure)?;
        let mnemonic = Mnemonic::from_entropy_in(Language::English, entropy.as_ref())
            .map_err(|_| Status::InternalPanic)?;
        let mut encoded = Zeroizing::new([0_u8; MAX_12_WORD_MNEMONIC_LENGTH]);
        let encoded_length = write_mnemonic_ascii(&mnemonic, encoded.as_mut())?;

        // SAFETY: the Kotlin binding guarantees a writable output buffer of the maximum mnemonic
        // length and a writable output-length pointer.
        unsafe {
            ptr::copy_nonoverlapping(encoded.as_ptr(), output, encoded_length);
            *length = encoded_length;
        }
        Ok(())
    })
}

/// Validates an ASCII, normalized English BIP-39 mnemonic.
///
/// # Safety
/// `input` must reference `length` readable bytes for the duration of this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_validate_mnemonic(input: *const u8, length: usize) -> i32 {
    ffi_status(|| {
        // SAFETY: The caller promised `length` readable bytes at `input`.
        let input = unsafe { slice::from_raw_parts(input, length) };
        parse_mnemonic(input).map(|_| ())
    })
}

/// Derives the private key at m/44'/60'/account'/0/address from an English BIP-39 mnemonic.
/// Account and address indices with the hardened bit already set are rejected.
///
/// # Safety
/// `mnemonic` must reference `mnemonic_length` readable bytes. `output` must reference exactly
/// 32 writable bytes. `passphrase` must reference `passphrase_length` readable bytes,
/// unless its length is zero, when it may be null. All pointers must remain valid during the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_derive_private_key(
    mnemonic: *const u8,
    mnemonic_length: usize,
    passphrase: *const u8,
    passphrase_length: usize,
    account_index: u32,
    address_index: u32,
    output: *mut u8,
) -> i32 {
    ffi_status(|| {
        // SAFETY: The caller promised `mnemonic_length` readable bytes at `mnemonic`.
        let mnemonic = unsafe { slice::from_raw_parts(mnemonic, mnemonic_length) };
        let mnemonic = parse_mnemonic(mnemonic)?;
        let passphrase = if passphrase_length == 0 {
            &[]
        } else {
            // SAFETY: the caller guarantees a readable passphrase buffer of this length.
            unsafe { slice::from_raw_parts(passphrase, passphrase_length) }
        };
        let seed = mnemonic_seed(&mnemonic, passphrase)?;
        let mut derivation_path = DerivationPath::default();
        derivation_path.extend([
            ChildNumber::new(44, true).map_err(|_| Status::InternalPanic)?,
            ChildNumber::new(60, true).map_err(|_| Status::InternalPanic)?,
            ChildNumber::new(account_index, true).map_err(|_| Status::InvalidArgument)?,
            ChildNumber::new(0, false).map_err(|_| Status::InternalPanic)?,
            ChildNumber::new(address_index, false).map_err(|_| Status::InvalidArgument)?,
        ]);
        let child = XPrv::derive_from_path(&seed[..], &derivation_path)
            .map_err(|_| Status::InternalPanic)?;
        let private_key = Zeroizing::new(child.to_bytes());

        // SAFETY: the Kotlin binding guarantees a writable 32-byte output buffer.
        unsafe { ptr::copy_nonoverlapping(private_key.as_ptr(), output, PRIVATE_KEY_LENGTH) };
        Ok(())
    })
}

/// Derives the lowercase Ethereum address bytes from a 32-byte secp256k1 private key.
///
/// # Safety
/// `private_key` must reference exactly 32 readable bytes and `output` must reference exactly 20
/// writable bytes. Both pointers must remain valid for the duration of this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_derive_address(private_key: *const u8, output: *mut u8) -> i32 {
    ffi_status(|| {
        // SAFETY: The ABI requires a 32-byte private-key input buffer.
        let private_key = unsafe { slice::from_raw_parts(private_key, PRIVATE_KEY_LENGTH) };
        let mut secret_key =
            SecretKey::from_slice(private_key).map_err(|_| Status::InvalidPrivateKey)?;
        let secp = secp256k1_context()?;
        let public_key = PublicKey::from_secret_key(secp, &secret_key).serialize_uncompressed();
        secret_key.non_secure_erase();
        let hash = Keccak256::digest(&public_key[1..]);

        // SAFETY: the Kotlin binding guarantees a writable 20-byte output buffer.
        unsafe {
            ptr::copy_nonoverlapping(
                hash[hash.len() - ADDRESS_LENGTH..].as_ptr(),
                output,
                ADDRESS_LENGTH,
            );
        }
        Ok(())
    })
}

/// Signs and encodes an EIP-1559 transaction into the caller-owned output buffer.
///
/// All quantity inputs are unsigned 256-bit big-endian values. The destination is exactly 20
/// bytes. A null `data` pointer is accepted only when `data_length` is zero.
///
/// # Safety
/// Fixed-size input pointers must reference their documented byte lengths. `data` must reference
/// `data_length` readable bytes unless its length is zero. `output` must reference enough writable
/// bytes for the encoded transaction, and `output_length` must reference one writable `usize`.
#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn ewc_sign_eip1559_transaction(
    private_key: *const u8,
    chain_id: u64,
    nonce: *const u8,
    max_priority_fee_per_gas: *const u8,
    max_fee_per_gas: *const u8,
    gas_limit: *const u8,
    destination: *const u8,
    value: *const u8,
    data: *const u8,
    data_length: usize,
    output: *mut u8,
    output_length: *mut usize,
) -> i32 {
    ffi_status(|| {
        // SAFETY: the Kotlin binding guarantees all fixed-size input buffers.
        let private_key = Zeroizing::new(unsafe { copy_array::<PRIVATE_KEY_LENGTH>(private_key) });
        let transaction = Eip1559Transaction {
            chain_id,
            nonce: unsafe { copy_array::<32>(nonce) },
            max_priority_fee_per_gas: unsafe { copy_array::<32>(max_priority_fee_per_gas) },
            max_fee_per_gas: unsafe { copy_array::<32>(max_fee_per_gas) },
            gas_limit: unsafe { copy_array::<32>(gas_limit) },
            destination: unsafe { copy_array::<ADDRESS_LENGTH>(destination) },
            value: unsafe { copy_array::<32>(value) },
            data: if data_length == 0 {
                &[]
            } else {
                // SAFETY: the Kotlin binding guarantees a readable calldata buffer.
                unsafe { slice::from_raw_parts(data, data_length) }
            },
        };
        let signed = sign_eip1559_transaction(&private_key, &transaction)?;
        // SAFETY: the caller promises an output buffer large enough for the encoded transaction.
        unsafe {
            ptr::copy_nonoverlapping(signed.as_ptr(), output, signed.len());
            *output_length = signed.len();
        }
        Ok(())
    })
}

struct Eip1559Transaction<'a> {
    chain_id: u64,
    nonce: [u8; 32],
    max_priority_fee_per_gas: [u8; 32],
    max_fee_per_gas: [u8; 32],
    gas_limit: [u8; 32],
    destination: [u8; ADDRESS_LENGTH],
    value: [u8; 32],
    data: &'a [u8],
}

fn sign_eip1559_transaction(
    private_key: &[u8; PRIVATE_KEY_LENGTH],
    transaction: &Eip1559Transaction<'_>,
) -> Result<Vec<u8>, Status> {
    validate_eip1559_transaction(transaction)?;
    let signing_payload = encode_eip1559_transaction(transaction, None);
    let signing_hash: [u8; 32] = Keccak256::digest(&signing_payload).into();
    let (y_parity, signature) = sign_recoverable(private_key, signing_hash)?;
    Ok(encode_eip1559_transaction(
        transaction,
        Some((y_parity, &signature[..32], &signature[32..])),
    ))
}

fn validate_eip1559_transaction(transaction: &Eip1559Transaction<'_>) -> Result<(), Status> {
    if transaction.chain_id == 0
        || transaction.max_priority_fee_per_gas > transaction.max_fee_per_gas
    {
        return Err(Status::InvalidTransaction);
    }
    Ok(())
}

fn sign_recoverable(
    private_key: &[u8; PRIVATE_KEY_LENGTH],
    signing_hash: [u8; 32],
) -> Result<(u8, [u8; 64]), Status> {
    let secp = secp256k1_context()?;
    let mut secret_key =
        SecretKey::from_slice(private_key).map_err(|_| Status::InvalidPrivateKey)?;
    let message = Message::from_digest(signing_hash);
    let signature = secp.sign_ecdsa_recoverable(&message, &secret_key);
    secret_key.non_secure_erase();
    let mut normalized = signature.to_standard();
    let original = normalized.serialize_compact();
    normalized.normalize_s();
    if original != normalized.serialize_compact() {
        return Err(Status::InternalPanic);
    }
    let (recovery_id, compact) = signature.serialize_compact();
    let y_parity = u8::try_from(recovery_id.to_i32()).map_err(|_| Status::InternalPanic)?;
    if y_parity > 1 {
        return Err(Status::InternalPanic);
    }
    Ok((y_parity, compact))
}

fn encode_eip1559_transaction(
    transaction: &Eip1559Transaction<'_>,
    signature: Option<(u8, &[u8], &[u8])>,
) -> Vec<u8> {
    let nonce = quantity_bytes(&transaction.nonce);
    let priority_fee = quantity_bytes(&transaction.max_priority_fee_per_gas);
    let max_fee = quantity_bytes(&transaction.max_fee_per_gas);
    let gas_limit = quantity_bytes(&transaction.gas_limit);
    let value = quantity_bytes(&transaction.value);
    let mut payload_length = transaction.chain_id.length()
        + nonce.length()
        + priority_fee.length()
        + max_fee.length()
        + gas_limit.length()
        + transaction.destination.length()
        + value.length()
        + transaction.data.length()
        + 1;
    if let Some((y_parity, r, s)) = signature {
        payload_length +=
            y_parity.length() + quantity_bytes_slice(r).length() + quantity_bytes_slice(s).length();
    }

    let mut encoded = Vec::with_capacity(payload_length + 10);
    encoded.push(0x02);
    Header {
        list: true,
        payload_length,
    }
    .encode(&mut encoded);
    transaction.chain_id.encode(&mut encoded);
    nonce.encode(&mut encoded);
    priority_fee.encode(&mut encoded);
    max_fee.encode(&mut encoded);
    gas_limit.encode(&mut encoded);
    transaction.destination.encode(&mut encoded);
    value.encode(&mut encoded);
    transaction.data.encode(&mut encoded);
    Header {
        list: true,
        payload_length: 0,
    }
    .encode(&mut encoded);
    if let Some((y_parity, r, s)) = signature {
        y_parity.encode(&mut encoded);
        quantity_bytes_slice(r).encode(&mut encoded);
        quantity_bytes_slice(s).encode(&mut encoded);
    }
    encoded
}

fn quantity_bytes(value: &[u8; 32]) -> &[u8] {
    quantity_bytes_slice(value)
}

fn quantity_bytes_slice(value: &[u8]) -> &[u8] {
    let first_nonzero = value
        .iter()
        .position(|byte| *byte != 0)
        .unwrap_or(value.len());
    &value[first_nonzero..]
}

unsafe fn copy_array<const LENGTH: usize>(input: *const u8) -> [u8; LENGTH] {
    let mut output = [0_u8; LENGTH];
    // SAFETY: the caller of this helper guarantees `LENGTH` readable bytes.
    unsafe { ptr::copy_nonoverlapping(input, output.as_mut_ptr(), LENGTH) };
    output
}

fn secp256k1_context() -> Result<&'static Secp256k1<All>, Status> {
    SECP256K1_CONTEXT
        .get_or_init(|| {
            let mut seed = Zeroizing::new([0_u8; 32]);
            getrandom::fill(seed.as_mut()).map_err(|_| Status::EntropyFailure)?;

            let mut context = Secp256k1::new();
            context.seeded_randomize(&seed);
            Ok(context)
        })
        .as_ref()
        .map_err(|status| *status)
}

fn ffi_status(operation: impl FnOnce() -> Result<(), Status>) -> i32 {
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(())) => Status::Ok.code(),
        Ok(Err(status)) => status.code(),
        Err(_) => Status::InternalPanic.code(),
    }
}

fn mnemonic_seed(mnemonic: &Mnemonic, passphrase: &[u8]) -> Result<Zeroizing<[u8; 64]>, Status> {
    let passphrase = std::str::from_utf8(passphrase).map_err(|_| Status::InvalidArgument)?;
    let normalized = Zeroizing::new(passphrase.nfkd().collect::<String>());
    Ok(Zeroizing::new(mnemonic.to_seed_normalized(&normalized)))
}

fn parse_mnemonic(input: &[u8]) -> Result<Mnemonic, Status> {
    let input = core::str::from_utf8(input).map_err(|_| Status::InvalidMnemonic)?;
    if !input.is_ascii() {
        return Err(Status::InvalidMnemonic);
    }
    Mnemonic::parse_in_normalized(Language::English, input).map_err(|_| Status::InvalidMnemonic)
}

fn write_mnemonic_ascii(mnemonic: &Mnemonic, output: &mut [u8]) -> Result<usize, Status> {
    let mut length = 0;
    for (index, word) in mnemonic.words().enumerate() {
        if index > 0 {
            *output.get_mut(length).ok_or(Status::InternalPanic)? = b' ';
            length += 1;
        }
        let word = word.as_bytes();
        let end = length
            .checked_add(word.len())
            .ok_or(Status::InternalPanic)?;
        output
            .get_mut(length..end)
            .ok_or(Status::InternalPanic)?
            .copy_from_slice(word);
        length = end;
    }
    Ok(length)
}

#[cfg(any(target_os = "android", target_os = "macos"))]
mod jni_bridge {
    use super::*;
    use jni::{
        JNIEnv,
        objects::{JByteArray, JClass, JIntArray},
        sys::{jbyteArray, jint, jintArray, jlong},
    };

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeAbiVersion(
        _env: JNIEnv,
        _class: JClass,
    ) -> jint {
        ewc_abi_version() as jint
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeCreateMnemonic(
        env: JNIEnv,
        _class: JClass,
        output: jbyteArray,
        length: jintArray,
    ) -> jint {
        let output = unsafe { JByteArray::from_raw(output) };
        let length = unsafe { JIntArray::from_raw(length) };
        let mut output_bytes = Zeroizing::new(vec![0_i8; MAX_12_WORD_MNEMONIC_LENGTH]);
        let mut actual_length = 0_usize;
        // SAFETY: `output_bytes` owns the maximum mnemonic buffer and `actual_length` is valid.
        let status = unsafe {
            ewc_create_mnemonic(output_bytes.as_mut_ptr().cast::<u8>(), &mut actual_length)
        };
        if env
            .set_byte_array_region(&output, 0, &output_bytes)
            .is_err()
        {
            return Status::InvalidArgument.code() as jint;
        }
        if status == Status::Ok.code()
            && env
                .set_int_array_region(&length, 0, &[actual_length as jint])
                .is_err()
        {
            return Status::InvalidArgument.code() as jint;
        }
        status as jint
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeValidateMnemonic(
        mut env: JNIEnv,
        _class: JClass,
        input: jbyteArray,
    ) -> jint {
        let input = match read_input(&mut env, input) {
            Ok(input) => input,
            Err(status) => return status as jint,
        };
        // SAFETY: `input` is an owned native buffer.
        unsafe { ewc_validate_mnemonic(input.as_ptr().cast::<u8>(), input.len()) as jint }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeDerivePrivateKey(
        mut env: JNIEnv,
        _class: JClass,
        mnemonic: jbyteArray,
        passphrase: jbyteArray,
        account_index: jint,
        address_index: jint,
        output: jbyteArray,
    ) -> jint {
        let mnemonic = match read_input(&mut env, mnemonic) {
            Ok(mnemonic) => mnemonic,
            Err(status) => return status as jint,
        };
        let passphrase = match read_input(&mut env, passphrase) {
            Ok(passphrase) => passphrase,
            Err(status) => return status as jint,
        };
        write_fixed_output(&mut env, output, PRIVATE_KEY_LENGTH, |result| {
            // SAFETY: the input buffers remain valid and result contains 32 writable bytes.
            unsafe {
                ewc_derive_private_key(
                    mnemonic.as_ptr().cast::<u8>(),
                    mnemonic.len(),
                    passphrase.as_ptr().cast::<u8>(),
                    passphrase.len(),
                    account_index as u32,
                    address_index as u32,
                    result.as_mut_ptr().cast::<u8>(),
                )
            }
        }) as jint
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeDeriveAddress(
        mut env: JNIEnv,
        _class: JClass,
        private_key: jbyteArray,
        output: jbyteArray,
    ) -> jint {
        let private_key = match read_input(&mut env, private_key) {
            Ok(private_key) => private_key,
            Err(status) => return status as jint,
        };
        if private_key.len() != PRIVATE_KEY_LENGTH {
            return Status::InvalidPrivateKey.code() as jint;
        }
        write_fixed_output(&mut env, output, ADDRESS_LENGTH, |result| {
            // SAFETY: both owned native buffers have the fixed sizes required by the ABI.
            unsafe {
                ewc_derive_address(
                    private_key.as_ptr().cast::<u8>(),
                    result.as_mut_ptr().cast::<u8>(),
                )
            }
        }) as jint
    }

    #[unsafe(no_mangle)]
    #[allow(clippy::too_many_arguments)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeSignEip1559Transaction(
        mut env: JNIEnv,
        _class: JClass,
        private_key: jbyteArray,
        chain_id: jlong,
        nonce: jbyteArray,
        max_priority_fee_per_gas: jbyteArray,
        max_fee_per_gas: jbyteArray,
        gas_limit: jbyteArray,
        destination: jbyteArray,
        value: jbyteArray,
        data: jbyteArray,
        output: jbyteArray,
        output_length: jintArray,
    ) -> jint {
        let private_key = match read_input(&mut env, private_key) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let nonce = match read_input(&mut env, nonce) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let priority_fee = match read_input(&mut env, max_priority_fee_per_gas) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let max_fee = match read_input(&mut env, max_fee_per_gas) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let gas_limit = match read_input(&mut env, gas_limit) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let destination = match read_input(&mut env, destination) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let value = match read_input(&mut env, value) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let data = match read_input(&mut env, data) {
            Ok(input) => input,
            Err(status) => return status.code() as jint,
        };
        let output = unsafe { JByteArray::from_raw(output) };
        let output_length = unsafe { JIntArray::from_raw(output_length) };
        let capacity = match env.get_array_length(&output) {
            Ok(length) if length >= 0 => length as usize,
            _ => return Status::InvalidArgument.code() as jint,
        };
        let mut output_bytes = Zeroizing::new(vec![0_i8; capacity]);
        let mut actual_length = 0_usize;
        // SAFETY: all input buffers have been copied to owned native memory with fixed lengths;
        // output buffers remain valid for the duration of the call.
        let status = unsafe {
            ewc_sign_eip1559_transaction(
                private_key.as_ptr().cast::<u8>(),
                chain_id as u64,
                nonce.as_ptr().cast::<u8>(),
                priority_fee.as_ptr().cast::<u8>(),
                max_fee.as_ptr().cast::<u8>(),
                gas_limit.as_ptr().cast::<u8>(),
                destination.as_ptr().cast::<u8>(),
                value.as_ptr().cast::<u8>(),
                data.as_ptr().cast::<u8>(),
                data.len(),
                output_bytes.as_mut_ptr().cast::<u8>(),
                &mut actual_length,
            )
        };
        if env
            .set_byte_array_region(&output, 0, &output_bytes)
            .is_err()
        {
            return Status::InvalidArgument.code() as jint;
        }
        if status == Status::Ok.code() {
            let Ok(actual_length) = jint::try_from(actual_length) else {
                return Status::InternalPanic.code() as jint;
            };
            if env
                .set_int_array_region(&output_length, 0, &[actual_length])
                .is_err()
            {
                return Status::InvalidArgument.code() as jint;
            }
        }
        status as jint
    }

    fn read_input(env: &mut JNIEnv, input: jbyteArray) -> Result<Zeroizing<Vec<i8>>, Status> {
        let input = unsafe { JByteArray::from_raw(input) };
        let length = match env.get_array_length(&input) {
            Ok(length) if length >= 0 => length as usize,
            _ => return Err(Status::InvalidArgument),
        };
        let mut bytes = Zeroizing::new(vec![0_i8; length]);
        if length > 0 && env.get_byte_array_region(&input, 0, &mut bytes).is_err() {
            return Err(Status::InvalidArgument);
        }
        Ok(bytes)
    }

    fn write_fixed_output(
        env: &mut JNIEnv,
        output: jbyteArray,
        length: usize,
        operation: impl FnOnce(&mut Zeroizing<Vec<i8>>) -> i32,
    ) -> i32 {
        let output = unsafe { JByteArray::from_raw(output) };
        let mut bytes = Zeroizing::new(vec![0_i8; length]);
        let status = operation(&mut bytes);
        if env.set_byte_array_region(&output, 0, &bytes).is_err() {
            return Status::InvalidArgument.code();
        }
        status
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const EXPECTED_SIGNED_TRANSACTION: &str = "02f87583aa36a707843b9aca008477359400825208943535353535353535353535353535353535353535880de0b6b3a764000080c080a0d203a655156d8a48583b27eb743fe8c6dfb5c0862763a80d3e07e55b718e4038a005c95b32d2ba7ad56455b64735834e041792bac244223533971db91a19c6caa3";
    const ERC20_TRANSFER_CALLDATA: &str = "a9059cbb0000000000000000000000009858effd232b4033e47d90003d41ec34ecaeda9400000000000000000000000000000000000000000000000000000000000f4240";
    const EXPECTED_SIGNED_ERC20_TRANSACTION: &str = "02f8b283aa36a707843b9aca00847735940082fde894dac17f958d2ee523a2206206994597c13d831ec780b844a9059cbb0000000000000000000000009858effd232b4033e47d90003d41ec34ecaeda9400000000000000000000000000000000000000000000000000000000000f4240c001a0d2cff7062b64372a1d503044996f508acddf169dcb1d2828c7bb248b9fa2c35fa072a2c6dd35dcb2e00220e335e7bbfcd85786dea11b36716708cbbfcf2eb535a4";

    #[test]
    fn signs_web3j_eip1559_reference_vector() {
        let mut private_key = [0_u8; 32];
        private_key[31] = 1;
        let transaction = reference_transaction();

        let signed = sign_eip1559_transaction(&private_key, &transaction).unwrap();

        assert_eq!(decode_hex(EXPECTED_SIGNED_TRANSACTION), signed);
    }

    #[test]
    fn signs_web3j_erc20_transfer_reference_vector() {
        let mut private_key = [0_u8; 32];
        private_key[31] = 1;
        let data = decode_hex(ERC20_TRANSFER_CALLDATA);
        let mut transaction = reference_transaction();
        transaction.gas_limit = quantity("fde8");
        transaction.destination = decode_hex("dac17f958d2ee523a2206206994597c13d831ec7")
            .try_into()
            .unwrap();
        transaction.value = [0; 32];
        transaction.data = &data;

        let signed = sign_eip1559_transaction(&private_key, &transaction).unwrap();

        assert_eq!(decode_hex(EXPECTED_SIGNED_ERC20_TRANSACTION), signed);
    }

    #[test]
    fn rejects_invalid_eip1559_transaction_fields() {
        let mut transaction = reference_transaction();
        transaction.chain_id = 0;
        assert_eq!(
            Err(Status::InvalidTransaction),
            validate_eip1559_transaction(&transaction),
        );

        transaction.chain_id = 1;
        transaction.max_priority_fee_per_gas = quantity("02");
        transaction.max_fee_per_gas = quantity("01");
        assert_eq!(
            Err(Status::InvalidTransaction),
            validate_eip1559_transaction(&transaction),
        );
    }

    #[test]
    fn encodes_zero_quantities_as_empty_rlp_strings() {
        let transaction = Eip1559Transaction {
            chain_id: 1,
            nonce: [0; 32],
            max_priority_fee_per_gas: [0; 32],
            max_fee_per_gas: [0; 32],
            gas_limit: [0; 32],
            destination: [0x11; 20],
            value: [0; 32],
            data: &[],
        };

        assert_eq!(
            decode_hex("02dd01808080809411111111111111111111111111111111111111118080c0"),
            encode_eip1559_transaction(&transaction, None),
        );
    }

    fn reference_transaction() -> Eip1559Transaction<'static> {
        Eip1559Transaction {
            chain_id: 11_155_111,
            nonce: quantity("07"),
            max_priority_fee_per_gas: quantity("3b9aca00"),
            max_fee_per_gas: quantity("77359400"),
            gas_limit: quantity("5208"),
            destination: [0x35; 20],
            value: quantity("0de0b6b3a7640000"),
            data: &[],
        }
    }

    fn quantity(hex: &str) -> [u8; 32] {
        let decoded = decode_hex(hex);
        let mut quantity = [0_u8; 32];
        quantity[32 - decoded.len()..].copy_from_slice(&decoded);
        quantity
    }

    fn decode_hex(hex: &str) -> Vec<u8> {
        assert_eq!(0, hex.len() % 2);
        (0..hex.len())
            .step_by(2)
            .map(|index| u8::from_str_radix(&hex[index..index + 2], 16).unwrap())
            .collect()
    }
}

#[cfg(test)]
mod passphrase_tests {
    use super::*;

    #[test]
    fn official_bip39_passphrase_vectors() {
        // BIP-39 reference vectors: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        for (phrase, expected) in [
            (
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            ),
            (
                "legal winner thank year wave sausage worth useful legal winner thank yellow",
                "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607",
            ),
        ] {
            let mnemonic = parse_mnemonic(phrase.as_bytes()).unwrap();
            let seed = mnemonic_seed(&mnemonic, b"TREZOR").unwrap();
            let expected: Vec<u8> = expected
                .as_bytes()
                .as_chunks::<2>()
                .0
                .iter()
                .map(|pair| u8::from_str_radix(std::str::from_utf8(pair).unwrap(), 16).unwrap())
                .collect();
            assert_eq!(expected, seed.as_slice());
        }
    }
}
