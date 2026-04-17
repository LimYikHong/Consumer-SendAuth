package com.worldline.mock.config;

import org.springframework.context.annotation.Configuration;

/**
 * Crypto configuration. RSA key pair is managed by KeyPairService
 * (auto-generated PEM files). AES session keys are sent per-request, encrypted
 * with RSA public key by the producer. No static AES key needed — each batch
 * has its own random AES key.
 */
@Configuration
public class CryptoConfig {
    // RSA key pair: managed by KeyPairService
    // AES session key: decrypted per-request from BatchRequestMessage.encryptedAesKey
}
