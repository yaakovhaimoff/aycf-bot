package com.aycf.flightFinder.features.UserCredentials;

import com.aycf.flightFinder.entity.User;
import com.aycf.flightFinder.features.encryption.IEncryptionService;
import com.aycf.flightFinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WizzCredentialProvider {

    private final UserRepository userRepository;
    private final IEncryptionService encryptionService;

    /**
     * Gets Wizz credentials for the currently authenticated user.
     * Extracts the user email from SecurityContext (set by JWT filter).
     */
    public WizzCredentials getCredentialsForCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return getCredentialsByEmail(email);
    }

    /**
     * Gets Wizz credentials by email.
     * Decrypts the stored password from database.
     */
    public WizzCredentials getCredentialsByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        String decryptedPassword = encryptionService.decrypt(
                user.getWizzPasswordEncrypted(),
                user.getWizzPasswordIv()
        );

        log.debug("Retrieved credentials for user: {}", email);
        return new WizzCredentials(user.getEmail(), decryptedPassword);
    }

    public record WizzCredentials(String email, String password) {}
}
