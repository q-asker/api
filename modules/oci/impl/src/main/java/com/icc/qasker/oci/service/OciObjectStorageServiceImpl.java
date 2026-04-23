package com.icc.qasker.oci.service;

import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.oci.properties.CdnProperties;
import com.icc.qasker.oci.properties.OciObjectStorageProperties;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

  private final CdnProperties cdnProperties;
  private final OciObjectStorageProperties ociProperties;
  private final UploadManager uploadManager;
  private final Timer pdfUploadTimer;
  private final Timer imageUploadTimer;

  public OciObjectStorageServiceImpl(
      CdnProperties cdnProperties,
      OciObjectStorageProperties ociProperties,
      UploadManager uploadManager,
      MeterRegistry registry) {
    this.cdnProperties = cdnProperties;
    this.ociProperties = ociProperties;
    this.uploadManager = uploadManager;
    this.pdfUploadTimer =
        Timer.builder("file.upload.oci.duration")
            .description("OCI Object Storage PDF 업로드 소요 시간")
            .tag("type", "pdf")
            .register(registry);
    this.imageUploadTimer =
        Timer.builder("file.upload.oci.duration")
            .description("OCI Object Storage 이미지 업로드 소요 시간")
            .tag("type", "image")
            .register(registry);
  }

  @Override
  public String uploadImage(
      InputStream inputStream, long contentLength, String contentType, String originalFileName) {
    return imageUploadTimer.record(
        () -> {
          String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
          String objectName = "images/" + UUID.randomUUID() + extension;

          String encodedFileName = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);

          PutObjectRequest putObjectRequest =
              PutObjectRequest.builder()
                  .namespaceName(ociProperties.namespace())
                  .bucketName(ociProperties.imageBucketName())
                  .objectName(objectName)
                  .contentType(contentType)
                  .opcMeta(java.util.Map.of("original-filename", encodedFileName))
                  .build();

          // stream does not support mark/reset 워닝 방지를 위해 BufferedInputStream으로 래핑
          InputStream bufferedInputStream =
              inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);

          UploadRequest uploadRequest =
              UploadRequest.builder(bufferedInputStream, contentLength)
                  .allowOverwrite(true)
                  .build(putObjectRequest);

          uploadManager.upload(uploadRequest);

          String finalUrl = cdnProperties.imageBaseUrl() + "/" + objectName;
          log.info("이미지 OCI 업로드 완료: {} -> {}", originalFileName, finalUrl);
          return finalUrl;
        });
  }

  @Override
  public String uploadPdf(Path pdfFile, String originalFileName) {
    return pdfUploadTimer.record(
        () -> {
          String uuid = UUID.randomUUID().toString();
          String objectName = uuid + ".pdf";

          String encodedFileName = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);

          PutObjectRequest putObjectRequest =
              PutObjectRequest.builder()
                  .namespaceName(ociProperties.namespace())
                  .bucketName(ociProperties.pdfBucketName())
                  .objectName(objectName)
                  .contentType("application/pdf")
                  .opcMeta(java.util.Map.of("original-filename", encodedFileName))
                  .build();

          // File 객체를 직접 전달하면 OCI SDK가 스트림 mark/reset 워닝 없이 멀티파트 업로드 처리
          UploadRequest uploadRequest =
              UploadRequest.builder(pdfFile.toFile()).allowOverwrite(true).build(putObjectRequest);

          uploadManager.upload(uploadRequest);

          String finalUrl = cdnProperties.fileBaseUrl() + "/" + objectName;
          log.info("PDF OCI 업로드 완료: {} -> {}", originalFileName, finalUrl);
          return finalUrl;
        });
  }
}
