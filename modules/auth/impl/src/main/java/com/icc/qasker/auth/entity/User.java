package com.icc.qasker.auth.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class User extends CreatedAt {

    @Id
    private String userId;
    private String role;
    private String provider;
    private String nickname;

    /**
     * Creates a User entity with the specified identifiers and profile attributes.
     *
     * @param userId   the unique identifier for the user
     * @param password accepted for builder compatibility; not stored on the entity
     * @param role     the user's role (e.g., "USER", "ADMIN")
     * @param provider the authentication provider (e.g., "google", "github")
     * @param nickname the user's display name
     */
    @Builder
    public User(String userId, String password, String role, String provider, String nickname) {
        this.userId = userId;
        this.role = role;
        this.provider = provider;
        this.nickname = nickname;
    }
}