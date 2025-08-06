package com.icc.qasker.quiz.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.sql.Timestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@NoArgsConstructor
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String username;
    private String password;
    private String role; //ROLE_USER, ROLE_ADMIN
    private String provider;
    @CreationTimestamp
    private Timestamp createDate;

    @Builder
    private User(String username, String password, String role, String provider,
        Timestamp createDate) {
        super();
        this.username = username;
        this.password = password;
        this.role = role;
        this.provider = provider;
        this.createDate = createDate;
    }
}
