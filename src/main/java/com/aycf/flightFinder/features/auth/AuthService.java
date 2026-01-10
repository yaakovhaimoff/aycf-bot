package com.aycf.flightFinder.features.auth;

import com.aycf.flightFinder.controller.model.AuthResponse;
import com.aycf.flightFinder.entity.User;
import com.aycf.flightFinder.features.encryption.IEncryptionService;
import com.aycf.flightFinder.features.jwt.IJwtService;
import com.aycf.flightFinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final UserRepository userRepository;
    private final IEncryptionService encryptionService;
    private final IJwtService jwtService;

    @Override
    public AuthResponse login(String email, String password) {
        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Decrypt stored password and compare
        String storedPassword = encryptionService.decrypt(
                user.getWizzPasswordEncrypted(),
                user.getWizzPasswordIv()
        );

        if (!storedPassword.equals(password)) {
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("User logged in successfully: {}", email);

        // Generate new JWT
        String token = jwtService.generateToken(email);

        return new AuthResponse(token, email);
    }
}
