package com.eyup.kervan.user.entity;

import org.springframework.modulith.NamedInterface;

// The auth module needs the role, but the rest of user/entity (User) stays internal.
@NamedInterface("role")
public enum Role {
    USER,
    ADMIN
}
