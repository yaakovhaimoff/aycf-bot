package com.aycf.flightFinder.features.registration;

import com.aycf.flightFinder.automation.webdriver.WebDriverFactory;
import com.aycf.flightFinder.controller.model.AuthResponse;
import com.aycf.flightFinder.entity.User;
import com.aycf.flightFinder.features.encryption.IEncryptionService;
import com.aycf.flightFinder.features.jwt.IJwtService;
import com.aycf.flightFinder.features.login.LoginService;
import com.aycf.flightFinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService implements IRegistrationService {

    private final UserRepository userRepository;
    private final IEncryptionService encryptionService;
    private final IJwtService jwtService;
    private final LoginService loginService;

    @Override
    @Transactional
    public AuthResponse register(String email, String password) {
        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Validate Wizz credentials via Selenium
        validateWizzCredentials(email, password);

        // Encrypt password
        IEncryptionService.EncryptedData encryptedPassword = encryptionService.encrypt(password);

        // Save user
        User user = User.builder()
                .email(email)
                .wizzPasswordEncrypted(encryptedPassword.cipherTextBase64())
                .wizzPasswordIv(encryptedPassword.ivBase64())
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", email);

        // Generate JWT
        String token = jwtService.generateToken(email);

        return new AuthResponse(token, email);
    }

    private void validateWizzCredentials(String email, String password) {
        WebDriver driver = null;
        try {
            driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
            boolean isValid = loginService.login(driver, email, password);
            if (!isValid) {
                throw new IllegalArgumentException("Invalid Wizz Air credentials");
            }
            log.info("Wizz credentials validated for: {}", email);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}
