package com.robot.platform.security.crypto;

/** Encrypts recoverable service secrets. Ciphertext is self-describing and authenticated. */
public interface SecretCipher {
    String encrypt(String plaintext);
    String decrypt(String payload);
}
