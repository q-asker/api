package com.icc.qasker.quiz.config;

import com.icc.qasker.global.properties.QAskerProperties;
import java.time.Duration;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@AllArgsConstructor
public class AiWebClientConfig {

    private final QAskerProperties qAskerProperties;

    @Primary
    @Bean("aiStreamClient")
    public WebClient aiGenerationClient() {
        return WebClient.builder()
            .baseUrl(qAskerProperties.getAiServerUrl())
            .build();
    }

    @Primary
    @Bean("aiRestClient")
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(40));

        return RestClient.builder()
            .baseUrl(qAskerProperties.getAiServerUrl())
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}