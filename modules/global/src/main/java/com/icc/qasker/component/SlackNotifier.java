package com.icc.qasker.component;

import com.icc.qasker.properties.SlackProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final WebClient.Builder webClientBuilder;
    private final SlackProperties slackProperties;

    public Mono<Void> notifyText(String text) {
        boolean enabled = slackProperties.isEnabled();
        String webhookUrl = slackProperties.getWebhookUrlNotify().toString();
        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) {
            return Mono.empty();
        }

        Map<String, Object> payload = Map.of(
            "text", text,
            "username", slackProperties.getUsernameNotify(),
            slackProperties.getIconNotify().startsWith("http")
                ? "icon_url" : "icon_emoji",
            slackProperties.getIconNotify()
        );

        return webClientBuilder.build()
            .post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .then()
            .onErrorResume(e -> {
                log.warn("Slack 알림 실패: {}", e.toString());
                return Mono.empty();
            });
    }
}