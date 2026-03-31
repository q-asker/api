package com.icc.qasker.oci.service;

import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.oci.properties.AwsCloudFrontProperties;
import com.icc.qasker.oci.properties.OciObjectStorageProperties;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Slf4j
@Service
@ConditionalOnBean(UploadManager.class)
public class OciObjectStorageServiceImpl implements ObjectStorageService {

  private final AwsCloudFrontProperties awsCloudFrontProperties;
  private final OciObjectStorageProperties ociProperties;
  private final UploadManager uploadManager;
  private final Timer uploadTimer;

  public OciObjectStorageServiceImpl(
      AwsCloudFrontProperties awsCloudFrontProperties,
      OciObjectStorageProperties ociProperties,
      UploadManager uploadManager,
      MeterRegistry registry) {
    this.awsCloudFrontProperties = awsCloudFrontProperties;
    this.ociProperties = ociProperties;
    this.uploadManager = uploadManager;
    this.uploadTimer =
        Timer.builder("file.upload.oci.duration")
            .description("OCI Object Storage 파일 업로드 소요 시간")
            .register(registry);
  }

  @Override
  public String uploadPdf(Path pdfFile, String originalFileName) {
    return uploadTimer.record(
        () -> {
          String uuid = UUID.randomUUID().toString();
          String objectName = uuid + ".pdf";

          String encodedFileName = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);

          PutObjectRequest putObjectRequest =
              PutObjectRequest.builder()
                  .namespaceName(ociProperties.namespace())
                  .bucketName(ociProperties.bucketName())
                  .objectName(objectName)
                  .contentType("application/pdf")
                  .opcMeta(java.util.Map.of("original-filename", encodedFileName))
                  .build();

          try {
            long contentLength = Files.size(pdfFile);
            InputStream inputStream = Files.newInputStream(pdfFile);

            UploadRequest uploadRequest =
                UploadRequest.builder(inputStream, contentLength)
                    .allowOverwrite(true)
                    .build(putObjectRequest);

            uploadManager.upload(uploadRequest);
          } catch (IOException e) {
            throw new RuntimeException("OCI Object Storage 업로드 실패: " + originalFileName, e);
          }

          String finalUrl = awsCloudFrontProperties.baseUrl() + "/" + objectName;
          log.info("PDF OCI 업로드 완료: {} -> {}", originalFileName, finalUrl);
          return finalUrl;
        });
  }
}
