package com.robot.platform.ai.model;

import com.robot.platform.ai.model.security.AesGcmAiSecretCipher;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmAiSecretCipherTest {

    @Test
    void roundTripsAndUsesRandomNonce() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        var cipher = new AesGcmAiSecretCipher(key);

        String a = cipher.encrypt("secret");
        String b = cipher.encrypt("secret");

        assertNotEquals(a, b);
        assertEquals("secret", cipher.decrypt(a));
        assertEquals("secret", cipher.decrypt(b));
    }

    @Test
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmAiSecretCipher(new byte[16]));
    }

    @Test
    void rejectsTamperedCiphertext() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 3);
        var cipher = new AesGcmAiSecretCipher(key);
        String encrypted = cipher.encrypt("secret");
        char replacement = encrypted.charAt(encrypted.length() - 2) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 2) + replacement
                + encrypted.substring(encrypted.length() - 1);

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(tampered));
    }
}
