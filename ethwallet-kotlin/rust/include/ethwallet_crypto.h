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
    EWC_STATUS_INSUFFICIENT_BUFFER = 3,
    EWC_STATUS_INVALID_ARGUMENT = 4,
    EWC_STATUS_ENTROPY_FAILURE = 5,
    EWC_STATUS_INTERNAL_PANIC = 6,
} ewc_status;

uint32_t ewc_abi_version(void);

int32_t ewc_create_mnemonic(uint8_t *output, size_t capacity, size_t *length);

int32_t ewc_validate_mnemonic(const uint8_t *input, size_t length);

int32_t ewc_derive_private_key(
    const uint8_t *mnemonic,
    size_t mnemonic_length,
    uint8_t output[32]
);

int32_t ewc_derive_address(const uint8_t private_key[32], uint8_t output[20]);

#ifdef __cplusplus
}
#endif

#endif
