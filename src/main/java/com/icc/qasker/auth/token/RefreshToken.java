package com.icc.qasker.auth.token;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.redis.core.RedisHash;

@NoArgsConstructor
@RedisHash(value = "refreshToken", timeToLive = 604800)
@Getter
@ToString
public class RefreshToken {

    private Long userId;
    @Id
    private String refreshToken; // hash로 저장

    public RefreshToken(Long userId, String refreshToken) {
        this.userId = userId;
        this.refreshToken = refreshToken;
    }

}
