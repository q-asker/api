package com.icc.qasker.global.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Configuration
public class WebClientConfig {

    @Value("${q-asker.ai-server-url}")
    private String aiServerUrl;
    @Value("${q-asker.ai-mocking-server-url}")
    private String aiMockingServerUrl;

    @Bean("aiWebClient")
    @Primary
    public WebClient aiWebClient(Builder builder) {
        return builder
            .baseUrl(aiServerUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean("aiMockingWebClient")
    public WebClient aiMockingWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(aiMockingServerUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
