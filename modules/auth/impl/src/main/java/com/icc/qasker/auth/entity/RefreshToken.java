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

    public RefreshToken(String userId, String rtHash, Instant expiresAt) {
        this.userId = userId;
        this.rtHash = rtHash;
        this.expiresAt = expiresAt;
    }

    public void rotate(String newRtHash, Instant newExpiresAt) {
        this.rtHash = newRtHash;
        this.expiresAt = newExpiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
