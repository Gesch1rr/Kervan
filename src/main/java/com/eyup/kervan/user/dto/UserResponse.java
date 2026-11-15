package com.eyup.kervan.user.dto;

import com.eyup.kervan.user.entity.Role;

import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {
}
