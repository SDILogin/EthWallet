use std::ptr;

use ethwallet_crypto::{
    ewc_abi_version, ewc_create_mnemonic, ewc_derive_address, ewc_derive_private_key,
    ewc_sign_eip1559_transaction, ewc_validate_mnemonic,
};

const STATUS_OK: i32 = 0;
const STATUS_INVALID_MNEMONIC: i32 = 1;
const STATUS_INVALID_PRIVATE_KEY: i32 = 2;
const STATUS_INSUFFICIENT_BUFFER: i32 = 3;
const STATUS_INVALID_ARGUMENT: i32 = 4;
const STATUS_INVALID_TRANSACTION: i32 = 7;

const CANONICAL_MNEMONIC: &[u8] =
    b"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
const CANONICAL_ETHEREUM_ADDRESS: [u8; 20] = [
    0x98, 0x58, 0xef, 0xfd, 0x23, 0x2b, 0x40, 0x33, 0xe4, 0x7d, 0x90, 0x00, 0x3d, 0x41, 0xec, 0x34,
    0xec, 0xae, 0xda, 0x94,
];
const PRIVATE_KEY_ONE_ETHEREUM_ADDRESS: [u8; 20] = [
    0x7e, 0x5f, 0x45, 0x52, 0x09, 0x1a, 0x69, 0x12, 0x5d, 0x5d, 0xfc, 0xb7, 0xb8, 0xc2, 0x65, 0x90,
    0x29, 0x39, 0x5b, 0xdf,
];

#[test]
fn abi_version_is_one() {
    assert_eq!(1, ewc_abi_version());
}

#[test]
fn validates_the_canonical_english_mnemonic() {
    // SAFETY: the mnemonic slice is readable for its full length.
    let status =
        unsafe { ewc_validate_mnemonic(CANONICAL_MNEMONIC.as_ptr(), CANONICAL_MNEMONIC.len()) };

    assert_eq!(STATUS_OK, status);
}

#[test]
fn derives_the_canonical_bip44_ethereum_address() {
    let mut private_key = [0_u8; 32];
    // SAFETY: all buffers meet the ABI sizes and remain valid for each call.
    let key_status = unsafe {
        ewc_derive_private_key(
            CANONICAL_MNEMONIC.as_ptr(),
            CANONICAL_MNEMONIC.len(),
            private_key.as_mut_ptr(),
        )
    };
    let mut address = [0_u8; 20];
    // SAFETY: both fixed-size buffers meet the ABI requirements.
    let address_status = unsafe { ewc_derive_address(private_key.as_ptr(), address.as_mut_ptr()) };

    assert_eq!(STATUS_OK, key_status);
    assert_eq!(STATUS_OK, address_status);
    assert_eq!(CANONICAL_ETHEREUM_ADDRESS, address);
}

#[test]
fn derives_the_known_address_for_private_key_one() {
    let mut private_key = [0_u8; 32];
    private_key[31] = 1;
    let mut address = [0_u8; 20];

    // SAFETY: both fixed-size buffers meet the ABI requirements.
    let status = unsafe { ewc_derive_address(private_key.as_ptr(), address.as_mut_ptr()) };

    assert_eq!(STATUS_OK, status);
    assert_eq!(PRIVATE_KEY_ONE_ETHEREUM_ADDRESS, address);
}

#[test]
fn invalid_inputs_return_stable_statuses_and_clear_outputs() {
    let mut private_key = [0xaa_u8; 32];
    // SAFETY: the output has the fixed ABI size; the mnemonic is deliberately invalid.
    let mnemonic_status = unsafe {
        ewc_derive_private_key(
            b"not a valid mnemonic".as_ptr(),
            20,
            private_key.as_mut_ptr(),
        )
    };
    assert_eq!(STATUS_INVALID_MNEMONIC, mnemonic_status);
    assert_eq!([0_u8; 32], private_key);

    let mut address = [0xaa_u8; 20];
    // SAFETY: both fixed-size buffers meet the ABI requirements; zero is an invalid scalar.
    let private_key_status =
        unsafe { ewc_derive_address([0_u8; 32].as_ptr(), address.as_mut_ptr()) };
    assert_eq!(STATUS_INVALID_PRIVATE_KEY, private_key_status);
    assert_eq!([0_u8; 20], address);

    // SAFETY: null and zero length are intentionally used to check ABI validation.
    let null_status = unsafe { ewc_validate_mnemonic(ptr::null(), 0) };
    assert_eq!(STATUS_INVALID_ARGUMENT, null_status);
}

#[test]
fn mnemonic_creation_requires_a_large_enough_caller_buffer() {
    let mut output = [0xaa_u8; 4];
    let mut length = usize::MAX;

    // SAFETY: output and length are valid writable buffers with the declared capacity.
    let status = unsafe { ewc_create_mnemonic(output.as_mut_ptr(), output.len(), &mut length) };

    assert_eq!(STATUS_INSUFFICIENT_BUFFER, status);
    assert_eq!([0_u8; 4], output);
    assert_eq!(0, length);
}

#[test]
fn created_mnemonic_is_english_and_validates() {
    let mut output = [0_u8; 128];
    let mut length = 0_usize;

    // SAFETY: output and length are valid writable buffers with the declared capacity.
    let status = unsafe { ewc_create_mnemonic(output.as_mut_ptr(), output.len(), &mut length) };
    // SAFETY: `length` is set only after a successful bounded write.
    let validation_status = unsafe { ewc_validate_mnemonic(output.as_ptr(), length) };

    assert_eq!(STATUS_OK, status);
    assert!(length <= output.len());
    assert_eq!(12, output[..length].split(|byte| *byte == b' ').count());
    assert_eq!(STATUS_OK, validation_status);
}

#[test]
fn signs_the_web3j_eip1559_reference_vector_through_ffi() {
    let inputs = SigningInputs::reference();
    let mut output = [0_u8; 512];
    let mut output_length = 0;

    let status = inputs.sign(&mut output, &mut output_length);

    assert_eq!(STATUS_OK, status);
    assert_eq!(
        decode_hex(
            "02f87583aa36a707843b9aca008477359400825208943535353535353535353535353535353535353535880de0b6b3a764000080c080a0d203a655156d8a48583b27eb743fe8c6dfb5c0862763a80d3e07e55b718e4038a005c95b32d2ba7ad56455b64735834e041792bac244223533971db91a19c6caa3"
        ),
        output[..output_length],
    );
}

#[test]
fn signing_rejects_invalid_values_without_writing_output() {
    let mut inputs = SigningInputs::reference();
    inputs.chain_id = 0;
    let mut output = [0xaa_u8; 512];
    let mut output_length = usize::MAX;

    let status = inputs.sign(&mut output, &mut output_length);

    assert_eq!(STATUS_INVALID_TRANSACTION, status);
    assert_eq!(usize::MAX, output_length);
    assert!(output.iter().all(|byte| *byte == 0xaa));

    inputs.chain_id = 1;
    inputs.max_priority_fee_per_gas.fill(0);
    inputs.max_fee_per_gas.fill(0);
    inputs.max_priority_fee_per_gas[31] = 2;
    inputs.max_fee_per_gas[31] = 1;
    output.fill(0xaa);
    output_length = usize::MAX;
    let status = inputs.sign(&mut output, &mut output_length);
    assert_eq!(STATUS_INVALID_TRANSACTION, status);
    assert_eq!(usize::MAX, output_length);
    assert!(output.iter().all(|byte| *byte == 0xaa));

    inputs.max_priority_fee_per_gas[31] = 0;
    inputs.max_fee_per_gas[31] = 0;
    inputs.private_key.fill(0);
    output.fill(0xaa);
    output_length = usize::MAX;
    let status = inputs.sign(&mut output, &mut output_length);
    assert_eq!(STATUS_INVALID_PRIVATE_KEY, status);
    assert_eq!(usize::MAX, output_length);
    assert!(output.iter().all(|byte| *byte == 0xaa));
}

struct SigningInputs {
    private_key: [u8; 32],
    chain_id: u64,
    nonce: [u8; 32],
    max_priority_fee_per_gas: [u8; 32],
    max_fee_per_gas: [u8; 32],
    gas_limit: [u8; 32],
    destination: [u8; 20],
    value: [u8; 32],
    data: Vec<u8>,
}

impl SigningInputs {
    fn reference() -> Self {
        let mut private_key = [0_u8; 32];
        private_key[31] = 1;
        Self {
            private_key,
            chain_id: 11_155_111,
            nonce: quantity("07"),
            max_priority_fee_per_gas: quantity("3b9aca00"),
            max_fee_per_gas: quantity("77359400"),
            gas_limit: quantity("5208"),
            destination: [0x35; 20],
            value: quantity("0de0b6b3a7640000"),
            data: Vec::new(),
        }
    }

    fn sign(&self, output: &mut [u8], output_length: &mut usize) -> i32 {
        // SAFETY: every fixed input and output buffer has the ABI-required size. Empty calldata is
        // represented by a non-null dangling pointer with zero length, which the ABI permits.
        unsafe {
            ewc_sign_eip1559_transaction(
                self.private_key.as_ptr(),
                self.chain_id,
                self.nonce.as_ptr(),
                self.max_priority_fee_per_gas.as_ptr(),
                self.max_fee_per_gas.as_ptr(),
                self.gas_limit.as_ptr(),
                self.destination.as_ptr(),
                self.value.as_ptr(),
                self.data.as_ptr(),
                self.data.len(),
                output.as_mut_ptr(),
                output_length,
            )
        }
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
