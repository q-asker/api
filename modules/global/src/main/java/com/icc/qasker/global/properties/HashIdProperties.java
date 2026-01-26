package com.icc.qasker.global.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties("q-asker.hashid")
public class HashIdProperties {

    private final String salt;
    private final int minLength;
}
