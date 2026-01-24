package com.icc.qasker.global.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "spring.security.jwt")
public class JwtProperties {

    private String secret;
    private long accessExpirationTime;
    private long refreshExpirationTime;
}
