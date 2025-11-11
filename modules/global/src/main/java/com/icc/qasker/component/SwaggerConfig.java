package com.icc.qasker.component;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    Info info = new Info()
        .title("q-asker API 문서");

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().components(new Components())
            .info(info);
    }
}
