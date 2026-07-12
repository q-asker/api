package com.icc.qasker.ai.config;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.icc.qasker.ai.ChunkProperties;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QAskerAiProperties.class)
@RequiredArgsConstructor
public class GeminiClientConfig {

  private final QAskerAiProperties aiProperties;

  @Bean
  @org.springframework.context.annotation.Profile("!test")
  public Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
    QAskerAiProperties.GeminiHttp http = aiProperties.getGeminiHttp();
    OkHttpClient httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(http.getConnectTimeout())
            .readTimeout(http.getReadTimeout())
            .callTimeout(http.getCallTimeout())
            .build();
    return Client.builder()
        .project(properties.getProjectId())
        .location(properties.getLocation())
        .vertexAI(true)
        // customHttpClient 사용 시 SDK가 HttpOptions.timeout 적용을 건너뛰므로 callTimeout을 위에서 직접 설정한다
        .clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
        .build();
  }

  @Bean
  @org.springframework.context.annotation.Profile("test")
  public Client googleGenAiClientTest() {
    return Client.builder()
        .apiKey("ci-dummy-key")
        .httpOptions(HttpOptions.builder().timeout(aiProperties.getChatTimeoutMs()).build())
        .build();
  }

  @Bean
  public ChunkProperties chunkProperties() {
    return aiProperties.getChunk();
  }
}
