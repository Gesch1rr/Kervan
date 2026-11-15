package com.eyup.kervan.auth.service;

import com.eyup.kervan.common.security.JwtConfig;
import com.eyup.kervan.common.security.JwtProperties;
import com.eyup.kervan.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "test-secret-test-secret-test-secret-123",
            "kervan",
            Duration.ofMinutes(15),
            Duration.ofDays(7)
    );
    private final JwtConfig config = new JwtConfig();
    private final JwtService jwtService = new JwtService(config.jwtEncoder(properties), properties);
    private final JwtDecoder decoder = config.jwtDecoder(properties);

    @Test
    void accessTokenCarriesUserIdRoleAndExpiry() {
        Jwt jwt = decoder.decode(jwtService.createAccessToken(42L, Role.ADMIN));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("kervan");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void everyTokenHasAUniqueId() {
        Jwt first = decoder.decode(jwtService.createAccessToken(42L, Role.USER));
        Jwt second = decoder.decode(jwtService.createAccessToken(42L, Role.USER));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void expiresInMatchesConfiguredTtl() {
        assertThat(jwtService.accessTokenTtlSeconds()).isEqualTo(900);
    }
}
