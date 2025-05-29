package com.icc.qasker.global.config;

import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HashIdConfig {

    @Value("${app.hashids.salt}")
    private String salt;

    @Value("${app.hashids.min-length}")
    private int minLength;

    @Bean
    public Hashids hashids() {
        return new Hashids(salt, minLength);
    }
}
