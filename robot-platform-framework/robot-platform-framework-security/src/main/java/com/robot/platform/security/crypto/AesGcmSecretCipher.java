package com.robot.platform.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM cipher. The configured key must be a base64-encoded 32-byte key. */
public final class AesGcmSecretCipher implements SecretCipher {
    private static final String VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(String base64Key) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Key);
            if (bytes.length != 32) {
                throw new IllegalArgumentException("robot secret master key must be a base64-encoded 32-byte key");
            }
            this.key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("robot secret master key is invalid", e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                    + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("unable to encrypt secret", e);
        }
    }

    @Override
    public String decrypt(String payload) {
        try {
            String[] parts = payload == null ? new String[0] : payload.split(":", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported encrypted secret payload");
            }
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            if (nonce.length != NONCE_BYTES) {
                throw new IllegalArgumentException("invalid encrypted secret nonce");
            }
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Do not distinguish malformed, wrong-key, and tampered ciphertext to callers.
            throw new IllegalStateException("encrypted secret cannot be authenticated", e);
        }
    }
}
