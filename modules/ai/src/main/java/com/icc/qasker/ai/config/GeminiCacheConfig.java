package com.icc.qasker.ai.config;

import com.google.genai.Client;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiCacheConfig {

    @Bean
    @ConditionalOnMissingBean
    public GoogleGenAiCachedContentService googleGenAiCachedContentService(Client genAiClient) {
        return new GoogleGenAiCachedContentService(genAiClient);
    }
}
