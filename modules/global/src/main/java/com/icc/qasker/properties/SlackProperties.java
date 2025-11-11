package com.icc.qasker.properties;

import java.net.URI;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    private final boolean enabled;
    private final URI webhookUrlNotify;
    private final String usernameNotify = "퀴즈생성 알림이";
    private final String iconNotify = ":white_check_mark:";
}