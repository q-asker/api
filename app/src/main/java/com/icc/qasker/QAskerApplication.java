package com.icc.qasker;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan("com.icc.qasker")
@EnableCaching
@EnableEncryptableProperties
public class QAskerApplication {

  public static void main(String[] args) {
    SpringApplication.run(QAskerApplication.class, args);
  }
}
