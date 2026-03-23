package com.icc.qasker.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiClientConfig {

  private static final int CHAT_TIMEOUT_MS = 90_000;

  @Bean
  public Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
    return Client.builder()
        .apiKey(properties.getApiKey())
        .httpOptions(HttpOptions.builder().timeout(CHAT_TIMEOUT_MS).build())
        .build();
  }
}
