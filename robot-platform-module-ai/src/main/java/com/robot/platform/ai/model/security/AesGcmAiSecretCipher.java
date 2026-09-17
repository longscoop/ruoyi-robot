package com.robot.platform.ai.model.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
public class AesGcmAiSecretCipher implements AiSecretCipher {
    private static final byte VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    @Autowired
    public AesGcmAiSecretCipher(@Value("${robot.ai.secret-key-base64:}") String base64Key) {
        this.key = base64Key == null || base64Key.isBlank() ? null : toKey(Base64.getDecoder().decode(base64Key));
    }

    public AesGcmAiSecretCipher(byte[] rawKey) {
        this.key = toKey(rawKey);
    }

    @Override
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer encoded = ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.length);
            encoded.put(VERSION).put(nonce).put(encrypted);
            return Base64.getEncoder().encodeToString(encoded.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt AI provider secret", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        byte[] payload = Base64.getDecoder().decode(ciphertext);
        if (payload.length <= 1 + NONCE_BYTES || payload[0] != VERSION) {
            throw new IllegalArgumentException("Unsupported AI secret ciphertext format");
        }
        ByteBuffer decoded = ByteBuffer.wrap(payload);
        decoded.get();
        byte[] nonce = new byte[NONCE_BYTES];
        decoded.get(nonce);
        byte[] encrypted = new byte[decoded.remaining()];
        decoded.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid AI secret ciphertext", e);
        }
    }

    private SecretKeySpec requireKey() {
        if (key == null) {
            throw new IllegalStateException("Set ROBOT_AI_SECRET_KEY_BASE64 to a Base64 encoded 32-byte key before managing AI provider secrets");
        }
        return key;
    }

    private static SecretKeySpec toKey(byte[] rawKey) {
        if (rawKey == null || rawKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("AI secret key must contain exactly 32 bytes");
        }
        return new SecretKeySpec(rawKey.clone(), "AES");
    }
}
