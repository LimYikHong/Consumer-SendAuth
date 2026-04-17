package com.worldline.mock.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages RSA key pair for the hybrid encryption scheme. - Generates RSA
 * 2048-bit key pair on first startup - Persists keys as PEM files so they
 * survive restarts - Exposes public key (PEM) for the producer to fetch via
 * internal API - Exposes private key for decrypting AES session keys
 */
@Service
@Slf4j
public class KeyPairService {

    @Value("${app.crypto.rsa.key-dir:./keys}")
    private String keyDir;

    @Value("${app.crypto.rsa.key-size:2048}")
    private int keySize;

    private static final String PUBLIC_KEY_FILE = "public_key.pem";
    private static final String PRIVATE_KEY_FILE = "private_key.pem";

    @Getter
    private PrivateKey privateKey;

    @Getter
    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        Path dir = Path.of(keyDir);
        Files.createDirectories(dir);

        Path pubPath = dir.resolve(PUBLIC_KEY_FILE);
        Path privPath = dir.resolve(PRIVATE_KEY_FILE);

        if (Files.exists(pubPath) && Files.exists(privPath)) {
            log.info("Loading existing RSA key pair from {}", keyDir);
            loadKeys(pubPath, privPath);
        } else {
            log.info("Generating new RSA-{} key pair...", keySize);
            generateAndSaveKeys(pubPath, privPath);
        }

        log.info("RSA public key fingerprint: {}", getPublicKeyFingerprint());
    }

    /**
     * Returns the public key as a PEM-formatted string.
     */
    public String getPublicKeyPem() {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Short fingerprint for logging/verification.
     */
    public String getPublicKeyFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(publicKey.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "N/A";
        }
    }

    private void generateAndSaveKeys(Path pubPath, Path privPath) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();

        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();

        // Save public key PEM
        writePem(pubPath.toFile(), "PUBLIC KEY", publicKey.getEncoded());
        // Save private key PEM
        writePem(privPath.toFile(), "PRIVATE KEY", privateKey.getEncoded());

        log.info("RSA key pair saved to {}", keyDir);
    }

    private void loadKeys(Path pubPath, Path privPath) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // Load public key
        byte[] pubBytes = readPemBytes(pubPath);
        this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes));

        // Load private key
        byte[] privBytes = readPemBytes(privPath);
        this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
    }

    private void writePem(File file, String type, byte[] encoded) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("-----BEGIN " + type + "-----\n");
            writer.write(base64);
            writer.write("\n-----END " + type + "-----\n");
        }
    }

    private byte[] readPemBytes(Path path) throws IOException {
        String pem = Files.readString(path);
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
