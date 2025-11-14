package com.icc.qasker.global.component;

import com.icc.qasker.global.properties.SlackProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient.Builder;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final Builder restClientBuilder;
    private final SlackProperties slackProperties;

    public void notifyText(String text) {
        boolean enabled = slackProperties.isEnabled();
        String webhookUrl = slackProperties.getWebhookUrlNotify().toString();
        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        Map<String, Object> payload = Map.of(
            "text", text,
            "username", slackProperties.getUsernameNotify(),
            slackProperties.getIconNotify().startsWith("http")
                ? "icon_url" : "icon_emoji",
            slackProperties.getIconNotify()
        );

        try {
            restClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Slack 알림 실패: {}", e.toString());
        }
    }
}