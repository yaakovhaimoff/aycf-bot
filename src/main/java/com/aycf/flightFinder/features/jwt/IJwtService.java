package com.aycf.flightFinder.features.jwt;

public interface IJwtService {

    String generateToken(String email);

    String extractEmail(String token);

    boolean isTokenValid(String token);
}
