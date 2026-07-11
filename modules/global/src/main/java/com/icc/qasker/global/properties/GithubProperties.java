package com.icc.qasker.global.properties;

import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "q-asker.github")
public class GithubProperties {

  private final boolean enabled;
  private final String token;
  private final String apiBaseUrl;
  private final String owner;
  private final String repo;
  private final List<String> feedbackLabels;

  public GithubProperties(
      boolean enabled,
      String token,
      @DefaultValue("https://api.github.com") String apiBaseUrl,
      String owner,
      String repo,
      @DefaultValue("feedback") List<String> feedbackLabels) {
    this.enabled = enabled;
    this.token = token;
    this.apiBaseUrl = apiBaseUrl;
    this.owner = owner;
    this.repo = repo;
    this.feedbackLabels = feedbackLabels;
  }
}
