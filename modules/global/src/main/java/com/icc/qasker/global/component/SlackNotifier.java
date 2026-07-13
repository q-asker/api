package com.icc.qasker.global.component;

import com.icc.qasker.global.properties.SlackProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient.Builder;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

  private final Builder restClientBuilder;
  private final SlackProperties slackProperties;

  @Async
  public void asyncNotifyText(String text) {
    sendSlack(
        slackProperties.getWebhookUrlNotify(),
        slackProperties.getUsernameNotify(),
        slackProperties.getIconNotify(),
        text);
  }

  /** 피드백 전용 채널로 알림을 보낸다. 전용 웹훅이 설정되지 않았으면 아무것도 보내지 않는다(폴백 없음). */
  @Async
  public void asyncNotifyFeedback(String text) {
    sendSlack(
        slackProperties.getWebhookUrlFeedback(),
        slackProperties.getUsernameFeedback(),
        slackProperties.getIconFeedback(),
        text);
  }

  private void sendSlack(String webhookUrl, String username, String icon, String text) {
    if (!slackProperties.isEnabled() || webhookUrl == null || webhookUrl.isBlank()) {
      return;
    }

    Map<String, Object> payload =
        Map.of(
            "text",
            text,
            "username",
            username,
            icon.startsWith("http") ? "icon_url" : "icon_emoji",
            icon);

    try {
      restClientBuilder
          .build()
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.warn("[Slack 알림 실패] 웹훅 전송 실패", e);
    }
  }
}
