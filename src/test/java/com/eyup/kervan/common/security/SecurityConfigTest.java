package com.eyup.kervan.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.SecuredController.class)
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailSecurityHandler.class,
        SecurityConfigTest.SecuredController.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @RestController
    static class SecuredController {

        @GetMapping("/api/v1/products/ping")
        String publicPing() {
            return "public";
        }

        @PostMapping("/api/v1/products/ping")
        String productWrite() {
            return "written";
        }

        @GetMapping("/api/v1/me/ping")
        String userPing() {
            return "user";
        }

        @GetMapping("/api/v1/admin/ping")
        @PreAuthorize("hasRole('ADMIN')")
        String adminPing() {
            return "admin";
        }
    }

    private String bearer(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject("42")
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .claim("roles", List.of(role))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return "Bearer " + token;
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/products/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void writingToPublicPathStillNeedsToken() throws Exception {
        mockMvc.perform(post("/api/v1/products/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/me/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/me/ping"));
    }

    @Test
    void invalidTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/me/ping").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/me/ping").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string("user"));
    }

    @Test
    void userCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }
}
