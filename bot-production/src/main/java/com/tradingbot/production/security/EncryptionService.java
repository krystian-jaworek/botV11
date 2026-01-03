package com.tradingbot.production.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for encrypting/decrypting sensitive data (ByBit API secrets).
 * Uses AES-128 encryption.
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final int KEY_LENGTH = 16; // 128 bits

    @Value("${app.encryption.secret-key}")
    private String secretKey;

    /**
     * Encrypt plaintext using AES
     */
    public String encrypt(String plaintext) {
        try {
            SecretKeySpec keySpec = createKeySpec();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt encrypted text using AES
     */
    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = createKeySpec();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Create AES key spec from configured secret key.
     * Pads or truncates the key to exactly 16 bytes (128 bits).
     */
    private SecretKeySpec createKeySpec() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

        // Ensure key is exactly 16 bytes
        byte[] paddedKey = new byte[KEY_LENGTH];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, KEY_LENGTH));

        return new SecretKeySpec(paddedKey, ALGORITHM);
    }
}
