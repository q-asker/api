package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
public class S3ServiceImpl implements S3Service {

  private final AwsCloudFrontProperties awsCloudFrontProperties;
  private final AwsS3Properties awsS3Properties;
  private final S3Client s3Client;
  private final Timer uploadTimer;

  public S3ServiceImpl(
      AwsCloudFrontProperties awsCloudFrontProperties,
      AwsS3Properties awsS3Properties,
      S3Client s3Client,
      MeterRegistry registry) {
    this.awsCloudFrontProperties = awsCloudFrontProperties;
    this.awsS3Properties = awsS3Properties;
    this.s3Client = s3Client;

    // S3 파일 업로드 Timer — 병렬 업로드 병목 식별용
    this.uploadTimer =
        Timer.builder("file.upload.s3.duration").description("S3 파일 업로드 소요 시간").register(registry);
  }

  @Override
  public String uploadPdf(Path pdfFile, String originalFileName) {
    return uploadTimer.record(
        () -> {
          String uuid = UUID.randomUUID().toString();
          String s3Key = uuid + ".pdf";

          Map<String, String> metadata = new HashMap<>();
          metadata.put(
              "original-filename", UriUtils.encode(originalFileName, StandardCharsets.UTF_8));

          s3Client.putObject(
              PutObjectRequest.builder()
                  .bucket(awsS3Properties.bucketName())
                  .key(s3Key)
                  .contentType("application/pdf")
                  .metadata(metadata)
                  .build(),
              RequestBody.fromFile(pdfFile));

          String finalUrl = awsCloudFrontProperties.baseUrl() + "/" + s3Key;
          log.info("PDF S3 업로드 완료: {} -> {}", originalFileName, finalUrl);
          return finalUrl;
        });
  }
}
