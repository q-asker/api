package com.icc.qasker.quiz.config;

import com.icc.qasker.global.properties.QAskerProperties;
import java.time.Duration;
import lombok.AllArgsConstructor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@AllArgsConstructor
public class RestClientConfig {

    private final QAskerProperties qAskerProperties;

    @Primary
    @Bean("aiStreamClient")
    public RestClient aiGenerationClient(QAskerProperties qAskerProperties) {
        // 1. Apache HttpClient 5 설정 (타임아웃 등)
        RequestConfig requestConfig = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(100))
            .setConnectTimeout(Timeout.ofSeconds(5))
            .build();

        CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();

        // 2. RestClient 빌드
        return RestClient.builder()
            .baseUrl(qAskerProperties.getAiServerUrl())
            .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
            .build();
    }

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