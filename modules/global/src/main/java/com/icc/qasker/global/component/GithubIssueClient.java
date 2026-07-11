package com.icc.qasker.global.component;

import com.icc.qasker.global.properties.GithubProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient.Builder;

/**
 * feedback_board에 저장된 사용자 피드백을 GitHub 이슈로 자동 등록한다. Slack 알림과 동일하게 비동기로 동작하며, 실패해도 원 요청(피드백 저장)에는
 * 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubIssueClient {

  private final Builder restClientBuilder;
  private final GithubProperties githubProperties;

  @Async
  public void asyncCreateIssue(String title, String body) {
    if (!githubProperties.isEnabled()
        || githubProperties.getToken() == null
        || githubProperties.getToken().isBlank()) {
      log.debug("[GitHub 이슈] 비활성화 상태 — 이슈 생성 건너뜀");
      return;
    }

    String uri =
        "%s/repos/%s/%s/issues"
            .formatted(
                githubProperties.getApiBaseUrl(),
                githubProperties.getOwner(),
                githubProperties.getRepo());

    Map<String, Object> payload =
        Map.of("title", title, "body", body, "labels", githubProperties.getFeedbackLabels());

    try {
      Map<String, Object> response =
          restClientBuilder
              .build()
              .post()
              .uri(uri)
              .header("Authorization", "Bearer " + githubProperties.getToken())
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});

      Object number = response == null ? null : response.get("number");
      log.info("[GitHub 이슈] 피드백 이슈 생성 완료 — #{}", number);
    } catch (Exception e) {
      log.warn("[GitHub 이슈] 이슈 생성 실패 — title={}", title, e);
    }
  }
}
