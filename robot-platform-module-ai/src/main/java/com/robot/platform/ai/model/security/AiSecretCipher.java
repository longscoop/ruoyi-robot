package com.robot.platform.ai.model.security;

public interface AiSecretCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
