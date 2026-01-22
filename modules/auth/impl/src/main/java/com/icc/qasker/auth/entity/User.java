package com.icc.qasker.auth.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class User extends CreatedAt {

    @Id
    private String userId;
    private String password;
    private String role;
    private String provider;
    private String nickname;
}
