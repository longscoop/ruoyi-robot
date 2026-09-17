package com.robot.platform.ai.model;

import com.robot.platform.ai.model.security.AesGcmAiSecretCipher;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsWrongSizedKey() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmAiSecretCipher(new byte[16]));
    }
}
