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

    @Builder
    public User(String userId, String password, String role, String provider, String nickname) {
        this.userId = userId;
        this.role = role;
        this.provider = provider;
        this.nickname = nickname;
    }
}
