package com.icc.qasker.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    Server local = new Server()
        .url("http://localhost:8080")
        .description("로컬 개발 서버");

    Server prod = new Server()
        .url("https://api.yourdomain.com")
        .description("프로덕션 서버");

    Info info = new Info()
        .title("q-asker API 문서");

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().components(new Components())
            .servers(List.of(local, prod))
            .info(info);
    }
}
