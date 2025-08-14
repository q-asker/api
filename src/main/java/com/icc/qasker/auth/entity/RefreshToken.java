package com.icc.qasker.auth.entity;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.data.redis.core.RedisHash;

@RedisHash(value = "refreshToken", timeToLive = 604800)
@Getter
@ToString
@RequiredArgsConstructor
public class RefreshToken {

    @Id
    private final String rtHash; // hash로 저장
    private final String userId;

}
