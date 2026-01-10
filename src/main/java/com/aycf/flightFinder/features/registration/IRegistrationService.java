package com.aycf.flightFinder.features.registration;

import com.aycf.flightFinder.controller.model.AuthResponse;

public interface IRegistrationService {

    AuthResponse register(String email, String password);
}
