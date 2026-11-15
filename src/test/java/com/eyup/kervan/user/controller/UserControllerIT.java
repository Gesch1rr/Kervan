package com.eyup.kervan.user.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.eyup.kervan.user.dto.UserResponse;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    private UserResponse newUser() {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        return userService.create(email, "password123", "Test User", Role.USER);
    }

    private String adminToken() {
        // role comes from the token; the id is irrelevant for admin endpoints
        return bearerToken(1L, Role.ADMIN);
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        UserResponse user = newUser();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearerToken(user.id(), Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updateMeChangesFullName() throws Exception {
        UserResponse user = newUser();
        String token = bearerToken(user.id(), Role.USER);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));
    }

    @Test
    void updateMeRejectsBlankName() throws Exception {
        UserResponse user = newUser();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearerToken(user.id(), Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
    }

    @Test
    void meForUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearerToken(999_999_999L, Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void listUsersRequiresAdmin() throws Exception {
        UserResponse user = newUser();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerToken(user.id(), Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanListUsersPageByPage() throws Exception {
        newUser();
        newUser();

        mockMvc.perform(get("/api/v1/users?size=1&page=0")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void unsupportedSortReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/users?sort=passwordHash")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void adminCanGetUserById() throws Exception {
        UserResponse user = newUser();

        mockMvc.perform(get("/api/v1/users/{id}", user.id())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void getUnknownUserByIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999999")
                        .header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
