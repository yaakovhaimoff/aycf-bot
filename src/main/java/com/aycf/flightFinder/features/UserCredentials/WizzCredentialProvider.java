package com.aycf.flightFinder.features.UserCredentials;

import com.aycf.flightFinder.entity.User;
import com.aycf.flightFinder.features.encryption.IEncryptionService;
import com.aycf.flightFinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WizzCredentialProvider {

    private final UserRepository userRepository;
    private final IEncryptionService encryptionService;

    @Value("${app.admin.user:}")
    private String configEmail;

    @Value("${app.admin.password:}")
    private String configPassword;

    /**
     * Gets Wizz credentials for the current context.
     * Falls back to config credentials (app.admin.user/password) when called
     * outside a web request (e.g. from MCP tools).
     */
    public WizzCredentials getCredentialsForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return getCredentialsByEmail(auth.getName());
        }
        if (StringUtils.hasText(configEmail)) {
            log.debug("No authenticated user, using config Wizz credentials");
            return new WizzCredentials(configEmail, configPassword);
        }
        throw new IllegalStateException("No authenticated user and app.admin.user is not configured");
    }

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
