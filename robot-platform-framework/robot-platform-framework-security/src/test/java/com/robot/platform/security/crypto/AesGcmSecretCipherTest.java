package com.robot.platform.security.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripsUsingVersionedAuthenticatedEncryption() {
        SecretCipher cipher = new AesGcmSecretCipher(key);
        assertThat(cipher.decrypt(cipher.encrypt("http-secret"))).isEqualTo("http-secret");
    }

    @Test
    void usesFreshNonceForEveryEncryption() {
        SecretCipher cipher = new AesGcmSecretCipher(key);
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void rejectsWrongKeyAndTampering() {
        SecretCipher cipher = new AesGcmSecretCipher(key);
        String payload = cipher.encrypt("secret");
        byte[] otherKeyBytes = new byte[32];
        otherKeyBytes[0] = 1;
        String otherKey = Base64.getEncoder().encodeToString(otherKeyBytes);
        assertThatThrownBy(() -> new AesGcmSecretCipher(otherKey).decrypt(payload)).isInstanceOf(IllegalStateException.class);
        String[] fields = payload.split(":");
        byte[] encrypted = Base64.getUrlDecoder().decode(fields[2]);
        encrypted[0] ^= 1;
        String tampered = fields[0] + ":" + fields[1] + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
