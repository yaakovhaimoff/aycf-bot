package com.aycf.flightFinder.components.UserCredentials;

import com.aycf.flightFinder.model.UserCredentials;

public interface ICredential {
    public void store(String sessionId, UserCredentials creds);
    public UserCredentials get(String sessionID);
    public void remove(String sessionID);

    class CredentialService {
    }
}
