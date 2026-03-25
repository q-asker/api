package com.icc.qasker.ai.config;

import com.icc.qasker.ai.properties.QAskerAiProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class GeminiFileRestClientConfig {

  private final QAskerAiProperties aiProperties;

  @Bean("geminiFileRestClient")
  public RestClient geminiRestClient() {
    QAskerAiProperties.FileClient fileClient = aiProperties.getFileClient();

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(fileClient.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(fileClient.getReadTimeoutMs()));

    return RestClient.builder().baseUrl(fileClient.getBaseUrl()).requestFactory(factory).build();
  }
}
