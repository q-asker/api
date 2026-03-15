package com.icc.qasker.global.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "q-asker.slack")
public class SlackProperties {

  private final boolean enabled;
  private final String webhookUrlNotify;
  private final String usernameNotify;
  private final String iconNotify;
  private final String webhookUrlError;
  private final String usernameError;
  private final String iconError;

  public SlackProperties(boolean enabled, String webhookUrlNotify, String webhookUrlError) {
    this.enabled = enabled;
    this.webhookUrlNotify = webhookUrlNotify;
    this.usernameNotify = "퀴즈생성 알림이";
    this.iconNotify = ":white_check_mark:";
    this.webhookUrlError = webhookUrlError;
    this.usernameError = "에러 알림이";
    this.iconError = ":rotating_light:";
  }
}
