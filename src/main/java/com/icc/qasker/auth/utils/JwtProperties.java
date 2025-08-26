package com.icc.qasker.auth.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessExpirationTime;
    private long refreshExpirationTime;

    public static String SECRET;
    public static long ACCESS_EXPIRATION_TIME;
    public static long REFRESH_EXPIRATION_TIME;

    public void setSecret(String secret) {
        this.secret = secret;
        SECRET = secret;
    }

    public void setAccessExpirationTime(long accessExpirationTime) {
        this.accessExpirationTime = accessExpirationTime;
        ACCESS_EXPIRATION_TIME = accessExpirationTime;
    }

    public void setRefreshExpirationTime(long refreshExpirationTime) {
        this.refreshExpirationTime = refreshExpirationTime;
        REFRESH_EXPIRATION_TIME = refreshExpirationTime;
    }
}


