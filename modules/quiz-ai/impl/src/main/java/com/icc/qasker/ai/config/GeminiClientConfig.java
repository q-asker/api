package com.icc.qasker.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.icc.qasker.ai.ChunkProperties;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import lombok.RequiredArgsConstructor;
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
  public Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
    return Client.builder()
        .apiKey(properties.getApiKey())
        .httpOptions(HttpOptions.builder().timeout(aiProperties.getChatTimeoutMs()).build())
        .build();
  }

  @Bean
  public ChunkProperties chunkProperties() {
    return aiProperties.getChunk();
  }
}
