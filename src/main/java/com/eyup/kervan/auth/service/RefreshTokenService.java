package com.eyup.kervan.auth.service;

import com.eyup.kervan.auth.entity.RefreshToken;
import com.eyup.kervan.auth.exception.InvalidRefreshTokenException;
import com.eyup.kervan.auth.repository.RefreshTokenRepository;
import com.eyup.kervan.common.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(
                userId,
                hash(rawToken),
                Instant.now().plus(jwtProperties.refreshTokenTtl())
        ));
        return rawToken;
    }

    // noRollbackFor keeps the "revoke all sessions" update when we throw right after it
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Long rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            // an already used/revoked token came back: assume it was stolen
            revokeAllSessions(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (refreshTokenRepository.revokeIfActive(token.getId(), now) == 0) {
            // a concurrent request used the same token first
            revokeAllSessions(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        return token.getUserId();
    }

    // idempotent and silent, so logout does not reveal whether a token was valid
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), Instant.now()));
    }

    private void revokeAllSessions(Long userId, Instant now) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, now);
        log.warn("Refresh token reuse detected for user {}, revoked {} active session(s)", userId, revoked);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
