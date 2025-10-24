package com.icc.qasker.global.component;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final WebClient.Builder webClientBuilder;

    @Value("${slack.enabled:}")
    private boolean enabled;

    @Value("${slack.webhook-url:}")
    private String webhookUrl;

    public Mono<Void> notifyText(String text) {
        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) {
            return Mono.empty();
        }
        return webClientBuilder.build()
            .post()
            .uri(webhookUrl) // Webhook은 절대경로로 호출
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("text", text))
            .retrieve()
            .toBodilessEntity()
            .then()
            .onErrorResume(e -> {
                log.warn("Slack 알림 실패: {}", e.toString());
                return Mono.empty(); // 알림 실패해도 본 플로우는 계속
            });
    }
}