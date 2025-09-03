package com.aycf.flightFinder.service;

import com.aycf.flightFinder.model.UserCredentials;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CredentialService implements ICredential {
    private final Map<String, UserCredentials> sessionCredentials = new HashMap<>();

    public void store(String sessionID, UserCredentials creds) {
        sessionCredentials.put(sessionID, creds);
    }

    public UserCredentials get(String sessionID) {
        return sessionCredentials.get(sessionID);
    }

    public void remove(String sessionID) {
        sessionCredentials.remove(sessionID);
    }
}
