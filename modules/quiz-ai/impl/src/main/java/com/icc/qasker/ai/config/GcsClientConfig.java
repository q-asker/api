package com.icc.qasker.ai.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Google Cloud Storage 클라이언트 설정. ADC(Application Default Credentials)로 인증한다. */
@Configuration
public class GcsClientConfig {

  @Bean
  public Storage gcsStorage() {
    return StorageOptions.getDefaultInstance().getService();
  }
}
