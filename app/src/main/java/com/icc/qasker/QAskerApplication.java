package com.icc.qasker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan("com.icc.qasker")
@EnableCaching
public class QAskerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QAskerApplication.class, args);
    }
}
