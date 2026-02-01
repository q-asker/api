package com.icc.qasker.global.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "q-asker.web")
public class QAskerProperties {

    private final String frontendDeployUrl;
    private final String frontendDevUrl;
    private final String aiServerUrl;
    private final String aiMockingServerUrl;
}
