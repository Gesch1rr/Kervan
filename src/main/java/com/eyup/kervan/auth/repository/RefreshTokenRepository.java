package com.eyup.kervan.auth.repository;

import com.eyup.kervan.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Conditional update: returns 0 if another request already revoked this token, so only one
    // of two concurrent refreshes with the same token can win.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.id = :id and t.revokedAt is null
            """)
    int revokeIfActive(Long id, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.userId = :userId and t.revokedAt is null
            """)
    int revokeAllForUser(Long userId, Instant now);
}
