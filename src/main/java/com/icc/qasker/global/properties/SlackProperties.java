package com.icc.qasker.global.properties;

import java.net.URI;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    private final boolean enabled;
    private final URI webhookUrlError;
    private final URI webhookUrlNotify;
    private final String usernameError;
    private final String iconError;
    private final String usernameNotify;
    private final String iconNotify;
}