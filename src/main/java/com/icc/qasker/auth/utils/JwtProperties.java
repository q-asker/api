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

    public static String secret;
    public static long accessExpirationTime;
    public static long refreshExpirationTime;
}
