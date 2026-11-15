package com.eyup.kervan.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kervan.admin")
public record AdminProperties(String email, String password) {
}
