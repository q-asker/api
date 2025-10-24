package com.icc.qasker;

import com.icc.qasker.auth.properties.RedisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(RedisProperties.class)
public class QAskerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QAskerApplication.class, args);
    }

}
