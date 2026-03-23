package com.icc.qasker.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiClientConfig {

  @Value("${q-asker.ai.chat-timeout-ms:90000}")
  private int chatTimeoutMs;

  @Bean
  public Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
    return Client.builder()
        .apiKey(properties.getApiKey())
        .httpOptions(HttpOptions.builder().timeout(chatTimeoutMs).build())
        .build();
  }
}
