#![no_main]

use alloy_rlp::Header;
use arbitrary::Arbitrary;
use ethwallet_crypto::ewc_sign_eip1559_transaction;
use ethwallet_crypto_fuzz::{
    SECP256K1, SENTINEL, SIGNED_TRANSACTION_CAPACITY_OVERHEAD, STATUS_INVALID_PRIVATE_KEY,
    STATUS_INVALID_TRANSACTION, STATUS_OK, ethereum_address, minimal_quantity,
};
use libfuzzer_sys::fuzz_target;
use secp256k1::{
    Message, SecretKey,
    ecdsa::{RecoverableSignature, RecoveryId, Signature},
};
use sha3::{Digest, Keccak256};

#[derive(Arbitrary, Debug)]
struct Input {
    private_key: [u8; 32],
    chain_id: u64,
    nonce: [u8; 32],
    max_priority_fee_per_gas: [u8; 32],
    max_fee_per_gas: [u8; 32],
    gas_limit: [u8; 32],
    destination: [u8; 20],
    value: [u8; 32],
    data: Vec<u8>,
    null_empty_data: bool,
}

fuzz_target!(|input: Input| {
    let private_key = Box::<[u8]>::from(input.private_key.as_slice());
    let nonce = Box::<[u8]>::from(input.nonce.as_slice());
    let max_priority_fee_per_gas = Box::<[u8]>::from(input.max_priority_fee_per_gas.as_slice());
    let max_fee_per_gas = Box::<[u8]>::from(input.max_fee_per_gas.as_slice());
    let gas_limit = Box::<[u8]>::from(input.gas_limit.as_slice());
    let destination = Box::<[u8]>::from(input.destination.as_slice());
    let value = Box::<[u8]>::from(input.value.as_slice());
    let data = Box::<[u8]>::from(input.data.as_slice());
    let data_pointer = if data.is_empty() && input.null_empty_data {
        std::ptr::null()
    } else {
        data.as_ptr()
    };
    let mut output = vec![SENTINEL; input.data.len() + SIGNED_TRANSACTION_CAPACITY_OVERHEAD];
    let mut output_length = usize::MAX;

    // SAFETY: each input is a separate heap buffer of its ABI size. The data pointer owns
    // `data.len()` bytes or is null with length zero. The output has the capacity that the Kotlin
    // bindings allocate.
    let status = unsafe {
        ewc_sign_eip1559_transaction(
            private_key.as_ptr(),
            input.chain_id,
            nonce.as_ptr(),
            max_priority_fee_per_gas.as_ptr(),
            max_fee_per_gas.as_ptr(),
            gas_limit.as_ptr(),
            destination.as_ptr(),
            value.as_ptr(),
            data_pointer,
            data.len(),
            output.as_mut_ptr(),
            &mut output_length,
        )
    };

    let secret_key = SecretKey::from_slice(&input.private_key);
    let expected = if input.chain_id == 0 || input.max_priority_fee_per_gas > input.max_fee_per_gas
    {
        STATUS_INVALID_TRANSACTION
    } else if secret_key.is_err() {
        STATUS_INVALID_PRIVATE_KEY
    } else {
        STATUS_OK
    };
    assert_eq!(expected, status);
    if status != STATUS_OK {
        assert_eq!(usize::MAX, output_length);
        assert!(output.iter().all(|byte| *byte == SENTINEL));
        return;
    }

    assert!(output_length <= output.len());
    assert!(output[output_length..].iter().all(|byte| *byte == SENTINEL));
    let signed = &output[..output_length];
    assert_eq!(0x02, signed[0]);
    let mut rest = &signed[1..];
    let header = Header::decode(&mut rest).expect("signed transaction is an RLP list");
    assert!(header.list);
    assert_eq!(header.payload_length, rest.len());

    let unsigned_fields_start = rest;
    let chain_id = input.chain_id.to_be_bytes();
    let expected_fields: [&[u8]; 8] = [
        minimal_quantity(&chain_id),
        minimal_quantity(&input.nonce),
        minimal_quantity(&input.max_priority_fee_per_gas),
        minimal_quantity(&input.max_fee_per_gas),
        minimal_quantity(&input.gas_limit),
        &input.destination,
        minimal_quantity(&input.value),
        &input.data,
    ];
    for expected_field in expected_fields {
        let field = Header::decode_bytes(&mut rest, false).expect("field is an RLP string");
        assert_eq!(expected_field, field);
    }
    let access_list = Header::decode_bytes(&mut rest, true).expect("access list is an RLP list");
    assert!(access_list.is_empty());
    let unsigned_fields = &unsigned_fields_start[..unsigned_fields_start.len() - rest.len()];

    let y_parity = Header::decode_bytes(&mut rest, false).expect("y parity is an RLP string");
    let r = Header::decode_bytes(&mut rest, false).expect("r is an RLP string");
    let s = Header::decode_bytes(&mut rest, false).expect("s is an RLP string");
    assert!(rest.is_empty());
    let recovery_id = match y_parity {
        [] => 0,
        [1] => 1,
        _ => panic!("y parity {y_parity:?} is not 0 or 1"),
    };
    assert!(!r.is_empty() && r.len() <= 32 && r[0] != 0);
    assert!(!s.is_empty() && s.len() <= 32 && s[0] != 0);
    let mut compact = [0_u8; 64];
    compact[32 - r.len()..32].copy_from_slice(r);
    compact[64 - s.len()..].copy_from_slice(s);

    let mut low_s = Signature::from_compact(&compact).expect("signature is valid");
    let original = low_s;
    low_s.normalize_s();
    assert_eq!(original, low_s);

    let mut signing_payload = vec![0x02];
    signing_payload.extend_from_slice(&rlp_list_header(unsigned_fields.len()));
    signing_payload.extend_from_slice(unsigned_fields);
    let signing_hash: [u8; 32] = Keccak256::digest(&signing_payload).into();
    let signature = RecoverableSignature::from_compact(
        &compact,
        RecoveryId::from_i32(recovery_id).expect("recovery id is 0 or 1"),
    )
    .expect("signature is valid");
    let signer = SECP256K1
        .recover_ecdsa(&Message::from_digest(signing_hash), &signature)
        .expect("signer is recoverable");
    let secret_key = secret_key.expect("private key is valid");
    let expected_signer = ethereum_address(&secret_key);
    let signer_hash = Keccak256::digest(&signer.serialize_uncompressed()[1..]);
    assert_eq!(expected_signer[..], signer_hash[12..]);
});

fn rlp_list_header(payload_length: usize) -> Vec<u8> {
    if payload_length < 56 {
        vec![0xc0 + payload_length as u8]
    } else {
        let length = minimal_quantity(&payload_length.to_be_bytes()).to_vec();
        let mut header = vec![0xf7 + length.len() as u8];
        header.extend_from_slice(&length);
        header
    }
}
