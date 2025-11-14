package com.icc.qasker.global.properties;

import java.net.URI;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    private final boolean enabled;
    private final URI webhookUrlNotify;
    private final String usernameNotify;
    private final String iconNotify;

    public SlackProperties(boolean enabled, URI webhookUrlNotify) {
        this.enabled = enabled;
        this.webhookUrlNotify = webhookUrlNotify;
        this.usernameNotify = "퀴즈생성 알림이";
        this.iconNotify = ":white_check_mark:";
    }
}