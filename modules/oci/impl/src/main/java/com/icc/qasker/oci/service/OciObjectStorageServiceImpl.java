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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

          // stream does not support mark/reset 워닝 방지를 위해 BufferedInputStream으로 래핑
          InputStream bufferedInputStream =
              inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);

          return upload(
              ociProperties.imageBucketName(),
              objectName,
              contentType,
              originalFileName,
              cdnProperties.imageBaseUrl(),
              request ->
                  UploadRequest.builder(bufferedInputStream, contentLength)
                      .allowOverwrite(true)
                      .build(request));
        });
  }

  @Override
  public String uploadPdf(Path pdfFile, String originalFileName) {
    return pdfUploadTimer.record(
        () -> {
          String objectName = UUID.randomUUID() + ".pdf";

          return upload(
              ociProperties.pdfBucketName(),
              objectName,
              "application/pdf",
              originalFileName,
              cdnProperties.fileBaseUrl(),
              // File 객체를 직접 전달하면 OCI SDK가 스트림 mark/reset 워닝 없이 멀티파트 업로드 처리
              request ->
                  UploadRequest.builder(pdfFile.toFile()).allowOverwrite(true).build(request));
        });
  }

  /**
   * PutObjectRequest 조립 → 업로드 → CDN URL 생성의 공통 골격. 소스 타입(스트림/파일)별 UploadRequest 조립만 {@code
   * uploadRequestFactory}로 위임한다.
   */
  private String upload(
      String bucketName,
      String objectName,
      String contentType,
      String originalFileName,
      String cdnBaseUrl,
      Function<PutObjectRequest, UploadRequest> uploadRequestFactory) {
    String encodedFileName = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);

    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .namespaceName(ociProperties.namespace())
            .bucketName(bucketName)
            .objectName(objectName)
            .contentType(contentType)
            .opcMeta(Map.of("original-filename", encodedFileName))
            .build();

    uploadManager.upload(uploadRequestFactory.apply(putObjectRequest));

    String finalUrl = cdnBaseUrl + "/" + objectName;
    log.info("OCI 업로드 완료: {} -> {}", originalFileName, finalUrl);
    return finalUrl;
  }
}
