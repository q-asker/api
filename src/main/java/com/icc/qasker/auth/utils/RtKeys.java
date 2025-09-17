package com.icc.qasker.auth.utils;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RtKeys {

    private final JwtProperties jwtProperties;

    public RtKeys(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String userSet(String userId) {
        return "rt:u:" + userId;
    }

    public Duration ttl() {
        return Duration.ofSeconds(jwtProperties.getRefreshExpirationTime());
    }
}
