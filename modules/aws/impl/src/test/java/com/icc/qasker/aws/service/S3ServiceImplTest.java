package com.icc.qasker.aws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ServiceImplTest {

  // 테스트 대상
  private S3ServiceImpl s3Service;
  // S3 파일 업로드용 클라이언트 (모킹)
  private S3Client s3Client;

  @BeforeEach
  void setUp() {
    s3Client = mock(S3Client.class);

    // 테스트용 S3 프로퍼티: 리전, 버킷명, 인증키, 파일 크기 제한 등
    AwsS3Properties awsS3Properties =
        new AwsS3Properties(
            "ap-northeast-2", // 서울 리전
            "test-bucket", // 테스트 버킷
            "test-access-key",
            "test-secret-key",
            36_700_160L, // 최대 파일 크기 35MB
            100, // presigned URL 유효 시간(초)
            255, // 최대 파일명 길이
            "application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation");

    // 테스트용 CloudFront 프로퍼티: 최종 URL의 도메인
    AwsCloudFrontProperties awsCloudFrontProperties =
        new AwsCloudFrontProperties("https://files.test.com");

    s3Service =
        new S3ServiceImpl(
            awsCloudFrontProperties, awsS3Properties, s3Client, new SimpleMeterRegistry());
  }

  // 보장: PDF 파일을 S3에 직접 업로드하고 CloudFront URL을 반환한다
  @Test
  @DisplayName("PDF 업로드 시 S3에 저장하고 CloudFront URL을 반환한다")
  void uploadPdf_pdfFile_returnsCloudFrontUrl() throws IOException {
    // Given: 임시 PDF 파일을 생성한다
    Path pdfFile = Files.createTempFile("test", ".pdf");
    Files.writeString(pdfFile, "dummy pdf content");

    // S3 putObject 호출 시 성공 응답을 반환하도록 모킹한다
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    try {
      // When: PDF 파일 업로드를 실행한다
      String cloudFrontUrl = s3Service.uploadPdf(pdfFile, "document.pdf");

      // Then: CloudFront 도메인으로 시작하고 .pdf로 끝나야 한다
      assertThat(cloudFrontUrl).startsWith("https://files.test.com/");
      assertThat(cloudFrontUrl).endsWith(".pdf");

      // Then: S3Client.putObject()가 호출되었는지 검증한다
      verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    } finally {
      Files.deleteIfExists(pdfFile);
    }
  }
}
