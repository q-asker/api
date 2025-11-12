package com.icc.qasker.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash(value = "refreshToken", timeToLive = 0)
@Getter
@ToString
@RequiredArgsConstructor

public class RefreshToken {

    @Id
    private final String rtHash;
    private final String userId;
}
