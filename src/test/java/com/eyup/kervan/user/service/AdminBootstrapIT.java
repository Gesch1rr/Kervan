package com.eyup.kervan.user.service;

import com.eyup.kervan.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void adminIsCreatedAtStartup() {
        assertThat(userService.adminExists()).isTrue();
    }
}
