package com.icc.qasker.global.component;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${q-asker.frontend-deploy-url}")
    private String frontEndDeployUrl;

    @Value("${q-asker.frontend-dev-url}")
    private String frontEndDevUrl;

    @Value("${q-asker.ai-server-url}")
    private String aiServerUrl;

    @Value("${q-asker.ai-mocking-server-url}")
    private String aiMockingServerUrl;


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(frontEndDeployUrl, frontEndDevUrl)
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .maxAge(3600);
    }

    @Bean("aiRestClient")
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));

        return RestClient.builder()
            .baseUrl(aiServerUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean("aiMockingRestClient")
    public RestClient aiMockingRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));
        
        return RestClient.builder()
            .baseUrl(aiMockingServerUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}