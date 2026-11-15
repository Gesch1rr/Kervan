package com.eyup.kervan.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final String OTHER_SECRET = "other-secret-0123456789abcdef0123456789abcdef";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(
            SECRET,
            "kervan",
            Duration.ofMinutes(15),
            Duration.ofDays(7)
    );

    @Test
    void encodedTokenCanBeDecoded() {
        JwtEncoder encoder = config.jwtEncoder(properties);
        JwtDecoder decoder = config.jwtDecoder(properties);
        Instant expiresAt = Instant.now().plus(properties.accessTokenTtl());

        Jwt jwt = decoder.decode(token(encoder, "kervan", expiresAt));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("roles")).contains("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("kervan");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtEncoder encoder = config.jwtEncoder(properties);
        JwtDecoder decoder = config.jwtDecoder(properties);

        assertThatThrownBy(() -> decoder.decode(token(
                encoder,
                "someone-else",
                Instant.now().plus(properties.accessTokenTtl())
        ))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtEncoder encoder = config.jwtEncoder(properties);
        JwtDecoder decoder = config.jwtDecoder(properties);

        assertThatThrownBy(() -> decoder.decode(token(
                encoder,
                "kervan",
                Instant.now().minus(Duration.ofMinutes(10))
        ))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties otherProperties = new JwtProperties(
                OTHER_SECRET,
                "kervan",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
        JwtEncoder otherEncoder = config.jwtEncoder(otherProperties);
        JwtDecoder decoder = config.jwtDecoder(properties);

        assertThatThrownBy(() -> decoder.decode(token(
                otherEncoder,
                "kervan",
                Instant.now().plus(properties.accessTokenTtl())
        ))).isInstanceOf(BadJwtException.class);
    }

    private String token(JwtEncoder encoder, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .claim("roles", List.of("USER"))
                .build();

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
    }
}
