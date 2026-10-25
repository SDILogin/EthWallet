#ifndef ETHWALLET_CRYPTO_H
#define ETHWALLET_CRYPTO_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define EWC_ABI_VERSION 1u

typedef enum ewc_status {
    EWC_STATUS_OK = 0,
    EWC_STATUS_INVALID_MNEMONIC = 1,
    EWC_STATUS_INVALID_PRIVATE_KEY = 2,
    EWC_STATUS_INVALID_ARGUMENT = 4,
    EWC_STATUS_ENTROPY_FAILURE = 5,
    EWC_STATUS_INTERNAL_PANIC = 6,
    EWC_STATUS_INVALID_TRANSACTION = 7,
} ewc_status;

uint32_t ewc_abi_version(void);

int32_t ewc_create_mnemonic(uint8_t *output, size_t *length);

int32_t ewc_validate_mnemonic(const uint8_t *input, size_t length);

/* Account and address indices must not exceed 2^31 - 1.
 * Passphrase is UTF-8; NULL is allowed only when passphrase_length is zero. */
int32_t ewc_derive_private_key(
    const uint8_t *mnemonic,
    size_t mnemonic_length,
    const uint8_t *passphrase,
    size_t passphrase_length,
    uint32_t account_index,
    uint32_t address_index,
    uint8_t output[32]
);

int32_t ewc_derive_address(const uint8_t private_key[32], uint8_t output[20]);

int32_t ewc_sign_eip1559_transaction(
    const uint8_t private_key[32],
    uint64_t chain_id,
    const uint8_t nonce[32],
    const uint8_t max_priority_fee_per_gas[32],
    const uint8_t max_fee_per_gas[32],
    const uint8_t gas_limit[32],
    const uint8_t destination[20],
    const uint8_t value[32],
    const uint8_t *data,
    size_t data_length,
    uint8_t *output,
    size_t *output_length
);

#ifdef __cplusplus
}
#endif

#endif
