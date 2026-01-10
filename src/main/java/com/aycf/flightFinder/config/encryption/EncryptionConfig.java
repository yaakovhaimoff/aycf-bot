package com.aycf.flightFinder.config.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Value("${ENCRYPTION_KEY}")
    private String encryptionKeyBase64;

    @Bean
    public SecretKey aesSecretKey() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException("ENCRYPTION_KEY environment variable must be set");
        }
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
        if (decodedKey.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes). Got " + decodedKey.length + " bytes.");
        }
        return new SecretKeySpec(decodedKey, "AES");
    }
}
