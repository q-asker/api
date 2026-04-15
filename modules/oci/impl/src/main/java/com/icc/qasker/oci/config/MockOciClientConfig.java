package com.icc.qasker.oci.config;

import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.oci.properties.CdnProperties;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** stress-test 프로파일에서 실제 OCI 연결 없이 업로드를 시뮬레이션한다. */
@Slf4j
@Configuration
@Profile("stress-test")
public class MockOciClientConfig {

  @Bean
  @Primary
  public ObjectStorageService mockObjectStorageService(CdnProperties cdnProperties) {
    return new ObjectStorageService() {
      @Override
      public String uploadPdf(Path pdfFile, String originalFileName) {
        log.info("Mock OCI Upload: {}", originalFileName);
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return cdnProperties.baseUrl() + "/" + UUID.randomUUID() + ".pdf";
      }

      @Override
      public String uploadImage(
          InputStream inputStream,
          long contentLength,
          String contentType,
          String originalFileName) {
        log.info("Mock OCI Image Upload: {}", originalFileName);
        String ext = originalFileName.substring(originalFileName.lastIndexOf("."));
        return cdnProperties.baseUrl() + "/images/" + UUID.randomUUID() + ext;
      }
    };
  }
}
