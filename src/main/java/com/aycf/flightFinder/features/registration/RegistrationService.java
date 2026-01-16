package com.aycf.flightFinder.features.registration;

import com.aycf.flightFinder.automation.webdriver.WebDriverSessionManager;
import com.aycf.flightFinder.controller.model.AuthResponse;
import com.aycf.flightFinder.entity.User;
import com.aycf.flightFinder.features.encryption.IEncryptionService;
import com.aycf.flightFinder.features.jwt.IJwtService;
import com.aycf.flightFinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService implements IRegistrationService {

    private final UserRepository userRepository;
    private final IEncryptionService encryptionService;
    private final IJwtService jwtService;
    private final WebDriverSessionManager sessionManager;

    @Override
    @Transactional
    public AuthResponse register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        validateWizzCredentials(email, password);

        IEncryptionService.EncryptedData encryptedPassword = encryptionService.encrypt(password);

        User user = User.builder()
                .email(email)
                .wizzPasswordEncrypted(encryptedPassword.cipherTextBase64())
                .wizzPasswordIv(encryptedPassword.ivBase64())
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", email);

        String token = jwtService.generateToken(email);

        return new AuthResponse(token, email);
    }

    private void validateWizzCredentials(String email, String password) {
        try {
            sessionManager.executeWithAuth(email, password, driver -> {
                log.info("Wizz credentials validated for: {}", email);
            });
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Wizz Air credentials", e);
        }
    }
}
