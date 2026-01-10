package com.aycf.flightFinder.features.auth;

import com.aycf.flightFinder.controller.model.AuthResponse;

public interface IAuthService {

    AuthResponse login(String email, String password);
}
