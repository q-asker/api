package com.icc.qasker.global.config;

import com.icc.qasker.global.properties.QAskerProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final QAskerProperties qAskerProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(qAskerProperties.getFrontendDevUrl(),
                qAskerProperties.getFrontendDeployUrl())
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .maxAge(3600);
    }
}
