package com.icc.qasker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan("com.icc.qasker")
public class QAskerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QAskerApplication.class, args);
    }

}
