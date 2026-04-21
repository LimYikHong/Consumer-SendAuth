package com.worldline.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts result CSV using the same hybrid RSA+AES scheme. Generates a new AES
 * session key, encrypts the CSV, then wraps the AES key with the producer's RSA
 * public key (or our own if the producer will decrypt with its private key — in
 * mock mode we use our own key pair for both directions).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvEncryptionService {

    private final KeyPairService keyPairService;

    /**
     * Encrypt CSV content using the given RSA public key. If no external key is
     * provided, falls back to this service's own key pair.
     *
     * @param csvContent plaintext CSV string
     * @param recipientPubKey RSA public key of the recipient (producer), or
     * null to use own key
     * @return EncryptedPayload containing Base64-encoded parts
     */
    public EncryptedPayload encrypt(String csvContent, PublicKey recipientPubKey) {
        try {
            // 1. Generate random AES-256 session key + IV
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            SecretKey aesKey = keyGen.generateKey();

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 2. Encrypt CSV with AES/CBC
            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
            byte[] encryptedCsv = aesCipher.doFinal(csvContent.getBytes(StandardCharsets.UTF_8));

            // 3. Encrypt AES key with recipient's RSA public key
            PublicKey rsaPublicKey = (recipientPubKey != null) ? recipientPubKey : keyPairService.getPublicKey();
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            log.debug("Encrypted CSV: {} bytes → {} bytes", csvContent.length(), encryptedCsv.length);

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(encryptedAesKey),
                    Base64.getEncoder().encodeToString(encryptedCsv),
                    Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            log.error("CSV encryption failed: {}", e.getMessage());
            throw new RuntimeException("CSV encryption failed", e);
        }
    }

    /**
     * Convenience overload — encrypts using own key pair (for Kafka flow).
     */
    public EncryptedPayload encrypt(String csvContent) {
        return encrypt(csvContent, null);
    }

    /**
     * Parse a PEM-encoded RSA public key string into a PublicKey object.
     */
    public PublicKey parsePublicKeyPem(String pem) {
        try {
            String base64 = pem
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            return java.security.KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse producer public key PEM", e);
        }
    }

    /**
     * Holds the three Base64-encoded parts of an encrypted payload.
     */
    public record EncryptedPayload(
            String encryptedAesKey,
            String encryptedContent,
            String iv
            ) {

    }
}
