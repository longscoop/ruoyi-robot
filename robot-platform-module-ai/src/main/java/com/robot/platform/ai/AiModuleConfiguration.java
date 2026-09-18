package com.robot.platform.ai;

import com.robot.platform.ai.model.security.AesGcmAiSecretCipher;
import com.robot.platform.ai.model.security.AiSecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class AiModuleConfiguration {

    @Bean
    public AiSecretCipher aiSecretCipher(@Value("${robot.ai.secret-key-base64:}") String secretKeyBase64) {
        if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
            throw new IllegalStateException("robot.ai.secret-key-base64 must be configured");
        }
        try {
            return new AesGcmAiSecretCipher(Base64.getDecoder().decode(secretKeyBase64));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("robot.ai.secret-key-base64 must be valid Base64 for a 32-byte key", exception);
        }
    }
}
