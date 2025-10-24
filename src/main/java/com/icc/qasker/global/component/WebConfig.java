package com.icc.qasker.global.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${q-asker.frontend-deploy-url}")
    private String frontEndDeployUrl;

    @Value("${q-asker.frontend-dev-url}")
    private String frontEndDevUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(frontEndDeployUrl, frontEndDevUrl)
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .maxAge(3600);
    }
}