package com.icc.qasker.ai.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiFileRestClientConfig {

    @Bean("geminiFileRestClient")
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .requestFactory(factory)
            .build();
    }
}
