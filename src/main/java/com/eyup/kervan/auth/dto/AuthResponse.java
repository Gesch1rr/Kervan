package com.eyup.kervan.auth.dto;

// expiresIn is in seconds
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
}
