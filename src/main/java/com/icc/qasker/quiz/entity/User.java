package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class User extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    private String username;
    private String password;
    private String role; //ROLE_USER, ROLE_ADMIN
    private String provider;
    private String nickname;

    @Builder
    private User(String id, String username, String password, String role, String provider,
        String nickname) {
        super();
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.provider = provider;
        this.nickname = nickname;
    }
}
