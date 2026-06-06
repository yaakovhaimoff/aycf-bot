package com.aycf.flightFinder.config.security;

import com.aycf.flightFinder.features.auth.IAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WizzAuthenticationProvider implements AuthenticationProvider {

    private final IAuthService authService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = (String) authentication.getCredentials();

        try {
            authService.login(email, password);
            log.info("Web login successful for: {}", email);
            return new UsernamePasswordAuthenticationToken(email, null, List.of());
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid credentials", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
