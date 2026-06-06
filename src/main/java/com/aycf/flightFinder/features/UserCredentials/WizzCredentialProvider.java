package com.aycf.flightFinder.features.UserCredentials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WizzCredentialProvider {

    @Value("${app.admin.user:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    public WizzCredentials getCredentialsForCurrentUser() {
        if (!StringUtils.hasText(email)) {
            throw new IllegalStateException("app.admin.user is not configured");
        }
        return new WizzCredentials(email, password);
    }

    public record WizzCredentials(String email, String password) {}
}
