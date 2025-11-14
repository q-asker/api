package com.icc.qasker.global.config;

import com.icc.qasker.global.properties.HashProperties;
import lombok.AllArgsConstructor;
import org.hashids.Hashids;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class HashIdConfig {

    private final HashProperties hashProperties;

    @Bean
    public Hashids hashids() {
        return new Hashids(hashProperties.getSalt(), hashProperties.getMinLength());
    }
}
