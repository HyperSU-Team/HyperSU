#ifndef MANAGER_SIGN_H
#define MANAGER_SIGN_H

// tiann/KernelSU
#define EXPECTED_SIZE_OFFICIAL 0x033b
#define EXPECTED_HASH_OFFICIAL "c371061b19d8c7d7d6133c6a9bafe198fa944e50c1b31c9d8daa8d7f1fc2d2d6"

// HyperSU-Team/HyperSU
#define EXPECTED_SIZE_HYPERSU 0x378
#define EXPECTED_HASH_HYPERSU "aaef52d74182a668b257065f27a53ecb3669047826c5090ed8102af6a7c3bcc4"

// KOWX712/KernelSU
#define EXPECTED_SIZE_KOWX712 0x375
#define EXPECTED_HASH_KOWX712 "484fcba6e6c43b1fb09700633bf2fb4758f13cb0b2f4457b80d075084b26c588"

typedef struct {
    unsigned size;
    const char *sha256;
} apk_sign_key_t;

#endif /* MANAGER_SIGN_H */
