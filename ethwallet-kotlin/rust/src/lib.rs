#![forbid(unsafe_op_in_unsafe_fn)]

use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    ptr, slice,
    sync::OnceLock,
};

use bip39::{Language, Mnemonic};
use bitcoin::{
    NetworkKind,
    bip32::{DerivationPath, Xpriv},
    secp256k1::{All, PublicKey, Secp256k1, SecretKey},
};
use sha3::{Digest, Keccak256};
use zeroize::Zeroizing;

const ABI_VERSION: u32 = 1;
const PRIVATE_KEY_LENGTH: usize = 32;
const ADDRESS_LENGTH: usize = 20;
const ENTROPY_LENGTH: usize = 16;
const MAX_12_WORD_MNEMONIC_LENGTH: usize = 107;
const ETHEREUM_DERIVATION_PATH: &str = "m/44'/60'/0'/0/0";

static SECP256K1_CONTEXT: OnceLock<Result<Secp256k1<All>, Status>> = OnceLock::new();

#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Status {
    Ok = 0,
    InvalidMnemonic = 1,
    InvalidPrivateKey = 2,
    InsufficientBuffer = 3,
    InvalidArgument = 4,
    EntropyFailure = 5,
    InternalPanic = 6,
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
/// `output` must reference `capacity` writable bytes and `length` must reference one writable
/// `usize`. The two pointers must remain valid for the duration of this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_create_mnemonic(
    output: *mut u8,
    capacity: usize,
    length: *mut usize,
) -> i32 {
    if output.is_null() || length.is_null() {
        return Status::InvalidArgument.code();
    }

    // SAFETY: The caller promises that `output` references `capacity` writable bytes and that
    // `length` is writable. Clearing before work prevents callers from observing stale secrets.
    unsafe {
        ptr::write_bytes(output, 0, capacity);
        *length = 0;
    }

    ffi_status(|| {
        let mut entropy = Zeroizing::new([0_u8; ENTROPY_LENGTH]);
        getrandom::fill(entropy.as_mut()).map_err(|_| Status::EntropyFailure)?;
        let mnemonic = Mnemonic::from_entropy_in(Language::English, entropy.as_ref())
            .map_err(|_| Status::InternalPanic)?;
        let mut encoded = Zeroizing::new([0_u8; MAX_12_WORD_MNEMONIC_LENGTH]);
        let encoded_length = write_mnemonic_ascii(&mnemonic, encoded.as_mut())?;

        if capacity < encoded_length {
            return Err(Status::InsufficientBuffer);
        }

        // SAFETY: `capacity >= encoded_length` was checked above and the caller supplied a valid
        // output buffer.
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
    if input.is_null() || length == 0 {
        return Status::InvalidArgument.code();
    }

    ffi_status(|| {
        // SAFETY: The caller promised `length` readable bytes at `input`.
        let input = unsafe { slice::from_raw_parts(input, length) };
        parse_mnemonic(input).map(|_| ())
    })
}

/// Derives the private key at m/44'/60'/0'/0/0 from an English BIP-39 mnemonic.
///
/// # Safety
/// `mnemonic` must reference `mnemonic_length` readable bytes. `output` must reference exactly
/// 32 writable bytes. Both pointers must remain valid for the duration of this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ewc_derive_private_key(
    mnemonic: *const u8,
    mnemonic_length: usize,
    output: *mut u8,
) -> i32 {
    if output.is_null() {
        return Status::InvalidArgument.code();
    }
    // SAFETY: The ABI requires a 32-byte output buffer.
    unsafe { ptr::write_bytes(output, 0, PRIVATE_KEY_LENGTH) };

    if mnemonic.is_null() || mnemonic_length == 0 {
        return Status::InvalidArgument.code();
    }

    ffi_status(|| {
        // SAFETY: The caller promised `mnemonic_length` readable bytes at `mnemonic`.
        let mnemonic = unsafe { slice::from_raw_parts(mnemonic, mnemonic_length) };
        let mnemonic = parse_mnemonic(mnemonic)?;
        let seed = Zeroizing::new(mnemonic.to_seed_normalized(""));
        let derivation_path: DerivationPath = ETHEREUM_DERIVATION_PATH
            .parse()
            .map_err(|_| Status::InternalPanic)?;
        let secp = secp256k1_context()?;

        // `Xpriv` is `Copy` in rust-bitcoin, therefore zeroization cannot cover compiler-created
        // copies. Keep it scoped to this derivation and never expose it over this ABI.
        let mut master = Xpriv::new_master(NetworkKind::Main, seed.as_ref())
            .map_err(|_| Status::InternalPanic)?;
        let mut child = master
            .derive_priv(secp, &derivation_path)
            .map_err(|_| Status::InternalPanic)?;
        master.private_key.non_secure_erase();
        let private_key = Zeroizing::new(child.private_key.secret_bytes());
        child.private_key.non_secure_erase();

        // SAFETY: The output pointer was checked and cleared at function entry.
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
    if output.is_null() {
        return Status::InvalidArgument.code();
    }
    // SAFETY: The ABI requires a 20-byte output buffer.
    unsafe { ptr::write_bytes(output, 0, ADDRESS_LENGTH) };

    if private_key.is_null() {
        return Status::InvalidArgument.code();
    }

    ffi_status(|| {
        // SAFETY: The ABI requires a 32-byte private-key input buffer.
        let private_key = unsafe { slice::from_raw_parts(private_key, PRIVATE_KEY_LENGTH) };
        let mut secret_key =
            SecretKey::from_slice(private_key).map_err(|_| Status::InvalidPrivateKey)?;
        let secp = secp256k1_context()?;
        let public_key = PublicKey::from_secret_key(secp, &secret_key).serialize_uncompressed();
        secret_key.non_secure_erase();
        let hash = Keccak256::digest(&public_key[1..]);

        // SAFETY: The output pointer was checked and cleared at function entry.
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
        sys::{jbyteArray, jint, jintArray},
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
        let output_length = match env.get_array_length(&output) {
            Ok(length) if length >= 0 => length as usize,
            _ => return Status::InvalidArgument.code() as jint,
        };
        let mut output_bytes = Zeroizing::new(vec![0_i8; output_length]);
        let mut actual_length = 0_usize;
        // SAFETY: `output_bytes` owns `output_length` writable bytes and `actual_length` is valid.
        let status = unsafe {
            ewc_create_mnemonic(
                output_bytes.as_mut_ptr().cast::<u8>(),
                output_bytes.len(),
                &mut actual_length,
            )
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
        // SAFETY: `input` is an owned, non-empty native buffer.
        unsafe { ewc_validate_mnemonic(input.as_ptr().cast::<u8>(), input.len()) as jint }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_me_sdi_ethwallet_kotlin_NativeCrypto_nativeDerivePrivateKey(
        mut env: JNIEnv,
        _class: JClass,
        mnemonic: jbyteArray,
        output: jbyteArray,
    ) -> jint {
        let mnemonic = match read_input(&mut env, mnemonic) {
            Ok(mnemonic) => mnemonic,
            Err(status) => return status as jint,
        };
        write_fixed_output(&mut env, output, PRIVATE_KEY_LENGTH, |result| {
            // SAFETY: both owned native buffers have the fixed sizes required by the ABI.
            unsafe {
                ewc_derive_private_key(
                    mnemonic.as_ptr().cast::<u8>(),
                    mnemonic.len(),
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

    fn read_input(env: &mut JNIEnv, input: jbyteArray) -> Result<Zeroizing<Vec<i8>>, Status> {
        if input.is_null() {
            return Err(Status::InvalidArgument);
        }
        let input = unsafe { JByteArray::from_raw(input) };
        let length = match env.get_array_length(&input) {
            Ok(length) if length > 0 => length as usize,
            _ => return Err(Status::InvalidArgument),
        };
        let mut bytes = Zeroizing::new(vec![0_i8; length]);
        if env.get_byte_array_region(&input, 0, &mut bytes).is_err() {
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
        if output.is_null() {
            return Status::InvalidArgument.code();
        }
        let output = unsafe { JByteArray::from_raw(output) };
        if env.get_array_length(&output).ok() != Some(length as i32) {
            return Status::InvalidArgument.code();
        }
        let mut bytes = Zeroizing::new(vec![0_i8; length]);
        let status = operation(&mut bytes);
        if env.set_byte_array_region(&output, 0, &bytes).is_err() {
            return Status::InvalidArgument.code();
        }
        status
    }
}
