package com.icc.qasker.auth.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class RefreshToken extends CreatedAt {

    @Id
    private String userId;
    private String rtHash;
    private Instant expiresAt;

    /**
     * Create a RefreshToken for the specified user with its stored hash and expiration time.
     *
     * @param userId    the user's identifier (primary key)
     * @param rtHash    the stored refresh token hash
     * @param expiresAt the instant when the token expires; may be {@code null} to indicate no expiration
     */
    public RefreshToken(String userId, String rtHash, Instant expiresAt) {
        this.userId = userId;
        this.rtHash = rtHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Replace the stored refresh token hash and its expiration timestamp.
     *
     * @param newRtHash     the new refresh token hash to store
     * @param newExpiresAt  the new expiration timestamp; may be {@code null} to indicate no expiration
     */
    public void rotate(String newRtHash, Instant newExpiresAt) {
        this.rtHash = newRtHash;
        this.expiresAt = newExpiresAt;
    }

    /**
     * Check whether the refresh token has passed its expiration time.
     *
     * @param now the reference time to compare against the token's expiration
     * @return `true` if `expiresAt` is before `now`, `false` otherwise
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}