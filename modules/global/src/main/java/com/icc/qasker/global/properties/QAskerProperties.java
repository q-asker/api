package com.icc.qasker.global.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "q-asker")
public class QAskerProperties {

    private final String frontendDeployUrl;
    private final String frontendDevUrl;
    private final String aiServerUrl;
    private final String aiMockingServerUrl;
}
