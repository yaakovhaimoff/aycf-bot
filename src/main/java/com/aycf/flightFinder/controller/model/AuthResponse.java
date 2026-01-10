package com.aycf.flightFinder.controller.model;

public record AuthResponse(
        String token,
        String email
) {}
