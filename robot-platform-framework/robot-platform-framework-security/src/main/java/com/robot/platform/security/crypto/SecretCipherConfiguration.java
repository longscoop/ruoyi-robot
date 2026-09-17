package com.robot.platform.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration intentionally fails closed when ROBOT_SECRET_MASTER_KEY is absent or malformed. */
@Configuration(proxyBeanMethods = false)
public class SecretCipherConfiguration {
    @Bean
    public SecretCipher secretCipher(@Value("${robot.security.secret-master-key:${ROBOT_SECRET_MASTER_KEY:}}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("robot.security.secret-master-key must be configured");
        }
        return new AesGcmSecretCipher(masterKey);
    }
}
