package com.icc.qasker.global.config;

import com.icc.qasker.global.properties.HashIdProperties;
import lombok.AllArgsConstructor;
import org.hashids.Hashids;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class HashIdConfig {

    private final HashIdProperties hashIdProperties;

    /**
     * Create a {@link Hashids} instance configured from the injected HashIdProperties.
     *
     * @return a {@link Hashids} initialized with the configured salt and minimum hash length
     */
    @Bean
    public Hashids hashids() {
        return new Hashids(hashIdProperties.getSalt(), hashIdProperties.getMinLength());
    }
}