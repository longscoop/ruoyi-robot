package com.robot.platform.ai.model.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmAiSecretCipher implements AiSecretCipher {
    private static final byte VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_CIPHERTEXT_BYTES = 16;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    public AesGcmAiSecretCipher(byte[] keyBytes) {
        this(keyBytes, new SecureRandom());
    }

    AesGcmAiSecretCipher(byte[] keyBytes, SecureRandom secureRandom) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("AI secret key must be exactly 32 bytes");
        }
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom must not be null");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
        this.secureRandom = secureRandom;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("AI provider secret must not be null");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.length);
            payload.put(VERSION).put(nonce).put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt AI provider secret", exception);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("AI provider secret ciphertext must not be blank");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length < 1 + NONCE_BYTES + MIN_CIPHERTEXT_BYTES) {
                throw new IllegalArgumentException("Invalid AI provider secret ciphertext");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != VERSION) {
                throw new IllegalArgumentException("Unsupported AI provider secret ciphertext version");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException
                    && ("Invalid AI provider secret ciphertext".equals(illegalArgumentException.getMessage())
                    || "Unsupported AI provider secret ciphertext version".equals(illegalArgumentException.getMessage()))) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid AI provider secret ciphertext", exception);
        }
    }
}
