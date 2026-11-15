package com.eyup.kervan.auth.controller;

import com.eyup.kervan.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerCreatesUser() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        register("not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() throws Exception {
        register(uniqueEmail(), "ş".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void loginReturnsAccessTokenThatWorks() throws Exception {
        String email = "Auth-" + UUID.randomUUID() + "@Test.Local";
        register(email, PASSWORD).andExpect(status().isCreated());

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String body = login(normalizedEmail, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken").value(not(emptyOrNullString())))
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(normalizedEmail));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginRejectsUnknownEmail() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshTokenIsStoredOnlyAsHash() throws Exception {
        String token = loginAndGetRefreshToken();

        Integer rawCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, token);
        Integer hashCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, sha256Hex(token));

        assertThat(rawCount).isZero();
        assertThat(hashCount).isEqualTo(1);
    }

    @Test
    void refreshRotatesTokens() throws Exception {
        String r1 = loginAndGetRefreshToken();

        String body = refresh(r1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken").value(not(emptyOrNullString())))
                .andReturn().getResponse().getContentAsString();

        String r2 = JsonPath.read(body, "$.refreshToken");
        String accessToken = JsonPath.read(body, "$.accessToken");

        assertThat(r2).isNotEqualTo(r1);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenRevokesAllSessions() throws Exception {
        String r1 = loginAndGetRefreshToken();

        String body = refresh(r1)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String r2 = JsonPath.read(body, "$.refreshToken");

        refresh(r1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // proves the revoke-all update was committed although an exception was thrown
        refresh(r2)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String r1 = loginAndGetRefreshToken();

        logout(r1).andExpect(status().isNoContent());

        refresh(r1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        logout(r1).andExpect(status().isNoContent());
    }

    @Test
    void logoutWithUnknownTokenIsSilent() throws Exception {
        logout("unknown-token").andExpect(status().isNoContent());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String r1 = loginAndGetRefreshToken();

        jdbcTemplate.update(
                "update refresh_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                sha256Hex(r1));

        refresh(r1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        refresh("unknown-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@test.local";
    }

    private String loginAndGetRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String body = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","fullName":"Auth User"}
                        """.formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
    }

    // computed independently of production code on purpose
    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
