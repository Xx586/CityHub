package com.cityhub.auth;

public record TokenResponse(String token, long expiresInSeconds) {
}

