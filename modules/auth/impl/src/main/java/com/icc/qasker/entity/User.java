package com.icc.qasker.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class User extends CreatedAt {

    @Id
    private String userId;
    private String password;
    private String role;
    private String provider;
    private String nickname;

    @Builder
    private User(String userId, String password, String role, String provider,
        String nickname) {
        super();
        this.userId = userId;
        this.password = password;
        this.role = role;
        this.provider = provider;
        this.nickname = nickname;
    }
}
