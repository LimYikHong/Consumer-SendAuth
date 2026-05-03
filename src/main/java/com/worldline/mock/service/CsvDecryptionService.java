package com.worldline.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Hybrid decryption service (RSA + AES).
 *
 * Flow per batch request: 1. Producer generates a random AES-256 session key 2.
 * Producer encrypts CSV with AES/CBC using that session key 3. Producer
 * encrypts the AES key with Consumer's RSA public key 4. Both encrypted AES key
 * + encrypted CSV are sent via Kafka 5. This service decrypts AES key with RSA
 * private key, then decrypts CSV
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvDecryptionService {

    private final KeyPairService keyPairService;

    /**
     * Decrypt the CSV content using hybrid RSA+AES scheme.
     *
     * @param encryptedAesKeyBase64 RSA-encrypted AES session key (Base64)
     * @param encryptedCsvBase64 AES-encrypted CSV content (Base64)
     * @param ivBase64 AES IV (Base64), required for CBC mode
     * @return plaintext CSV string
     */
    public String decrypt(String encryptedAesKeyBase64, String encryptedCsvBase64, String ivBase64) {
        // Null-check all inputs before attempting decryption
        if (encryptedAesKeyBase64 == null || encryptedAesKeyBase64.isBlank()) {
            throw new RuntimeException("encryptedAesKey is null/empty — check producer Kafka message field name matches 'encryptedAesKey'");
        }
        if (encryptedCsvBase64 == null || encryptedCsvBase64.isBlank()) {
            throw new RuntimeException("encryptedCsvContent is null/empty — check producer Kafka message field name matches 'encryptedCsvContent'");
        }
        if (ivBase64 == null || ivBase64.isBlank()) {
            log.warn("IV is null/empty — will attempt ECB mode (not recommended)");
        }

        try {
            // Step 1: Decrypt AES session key using RSA private key
            SecretKey aesKey = decryptAesKey(encryptedAesKeyBase64);
            log.debug("AES session key decrypted successfully");

            // Step 2: Decrypt CSV content using AES session key
            byte[] encryptedCsvBytes = Base64.getDecoder().decode(encryptedCsvBase64);

            Cipher aesCipher;
            if (ivBase64 != null && !ivBase64.isBlank()) {
                aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] ivBytes = Base64.getDecoder().decode(ivBase64);
                aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
            } else {
                aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
            }

            byte[] decryptedBytes = aesCipher.doFinal(encryptedCsvBytes);
            String csv = new String(decryptedBytes, StandardCharsets.UTF_8);
            log.debug("Decrypted CSV content length: {} chars", csv.length());
            return csv;

        } catch (Exception e) {
            log.error("Hybrid decryption failed: {}", e.getMessage());
            throw new RuntimeException("CSV decryption failed", e);
        }
    }

    /**
     * Decrypt the RSA-encrypted AES session key.
     */
    private SecretKey decryptAesKey(String encryptedAesKeyBase64) throws Exception {
        PrivateKey privateKey = keyPairService.getPrivateKey();
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedAesKeyBase64);

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);
        return new SecretKeySpec(aesKeyBytes, "AES");
    }
}
