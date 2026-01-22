package com.icc.qasker.global.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "spring.security.jwt")
public class JwtProperties {

    public static String SECRET;
    public static long ACCESS_EXPIRATION_TIME; // ms
    public static long REFRESH_EXPIRATION_TIME; // s
    private String secret;
    private long accessExpirationTime; // s
    private long refreshExpirationTime; // s

    public void setSecret(String secret) {
        this.secret = secret;
        SECRET = secret;
    }

    public void setAccessExpirationTime(long seconds) {
        this.accessExpirationTime = seconds;
        ACCESS_EXPIRATION_TIME = seconds * 1000; // ms 변경
    }

    public void setRefreshExpirationTime(long seconds) {
        this.refreshExpirationTime = seconds;
        REFRESH_EXPIRATION_TIME = seconds;
    }
}
