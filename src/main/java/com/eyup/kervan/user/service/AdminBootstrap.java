package com.eyup.kervan.user.service;

import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.exception.EmailAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final AdminProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.info("kervan.admin is not configured, skipping admin bootstrap");
            return;
        }
        if (userService.adminExists()) {
            return;
        }
        try {
            userService.create(properties.email(), properties.password(), "Administrator", Role.ADMIN);
            log.info("Created initial admin user {}", properties.email());
        } catch (EmailAlreadyExistsException | DataIntegrityViolationException e) {
            // another app instance may have created it at the same moment, or the email is already taken
            log.info("Admin email {} is already registered, skipping admin bootstrap", properties.email());
        }
    }
}
