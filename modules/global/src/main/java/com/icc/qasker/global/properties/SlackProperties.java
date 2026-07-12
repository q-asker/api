package com.icc.qasker.global.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "q-asker.slack")
public class SlackProperties {

  private final boolean enabled;
  private final String webhookUrlNotify;
  private final String webhookUrlFeedback;
  private final String usernameNotify;
  private final String iconNotify;
  private final String usernameFeedback;
  private final String iconFeedback;

  public SlackProperties(boolean enabled, String webhookUrlNotify, String webhookUrlFeedback) {
    this.enabled = enabled;
    this.webhookUrlNotify = webhookUrlNotify;
    this.webhookUrlFeedback = webhookUrlFeedback;
    this.usernameNotify = "퀴즈생성 알림이";
    this.iconNotify = ":white_check_mark:";
    this.usernameFeedback = "피드백 알림이";
    this.iconFeedback = ":memo:";
  }
}
