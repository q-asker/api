package com.icc.qasker.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtProperties {

    @Value("${q-jwt.secret}")
    private String SECRET;
    @Value("${q-jwt.expiration-time}")
    private int EXPIRATION_TIME;
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HEADER_STRING = "Authorization";
}
