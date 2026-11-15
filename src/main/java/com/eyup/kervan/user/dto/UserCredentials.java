package com.eyup.kervan.user.dto;

import com.eyup.kervan.user.entity.Role;

// what the auth module needs to check a login; never returned by the API
public record UserCredentials(Long id, String passwordHash, Role role, boolean enabled) {
}
