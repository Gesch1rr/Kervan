-- INCREMENT BY must match @SequenceGenerator(allocationSize = 50) on RefreshToken.
CREATE SEQUENCE refresh_tokens_seq START WITH 1 INCREMENT BY 50;

-- Only a SHA-256 hash of each token is stored, so a database leak does not leak usable tokens.
CREATE TABLE refresh_tokens (
    id         BIGINT      PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Used when all sessions of a user are revoked.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
