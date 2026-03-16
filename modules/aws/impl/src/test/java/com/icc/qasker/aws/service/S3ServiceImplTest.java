package com.icc.qasker.aws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.aws.ConvertService;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3ServiceImplTest {

  // 테스트 대상
  private S3ServiceImpl s3Service;
  // S3 파일 업로드용 클라이언트 (모킹)
  private S3Client s3Client;
  // Presigned URL 생성용 (모킹)
  private S3Presigner s3Presigner;
  // 파일 유효성 검증 서비스 (모킹 — 검증 로직은 별도 테스트)
  private S3ValidateService s3ValidateService;
  // PDF 변환 서비스 (모킹 — ConvertServiceImplTest에서 별도 검증)
  private ConvertService convertService;

  @BeforeEach
  void setUp() {
    // 모든 외부 의존성을 모킹하여 S3ServiceImpl의 로직만 격리 테스트한다
    s3Client = mock(S3Client.class);
    s3Presigner = mock(S3Presigner.class);
    s3ValidateService = mock(S3ValidateService.class);
    convertService = mock(ConvertService.class);

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

    // 모킹된 의존성을 주입하여 테스트 대상 서비스를 생성한다
    s3Service =
        new S3ServiceImpl(
            awsCloudFrontProperties,
            awsS3Properties,
            s3Presigner,
            s3Client,
            s3ValidateService,
            convertService);
  }

  // 클래스패스(src/test/resources/testfiles/)에서 InputStream으로 리소스를 로드하는 헬퍼
  private InputStream loadResource(String name) {
    return getClass().getClassLoader().getResourceAsStream("testfiles/" + name);
  }

  // 보장: PDF 파일의 presign 플로우가 정상 동작하여 프론트엔드가 S3에 직접 업로드할 수 있다
  @Test
  @DisplayName("PDF presign 요청 시 업로드 URL과 CloudFront URL을 반환한다")
  void requestPresign_pdfFile_returnsPresignResponse() throws Exception {
    // Given: PDF 파일에 대한 presign 요청 DTO를 생성한다
    PresignRequest request = new PresignRequest("document.pdf", "application/pdf", 1024L);

    // S3Presigner가 반환할 PresignedPutObjectRequest를 모킹한다
    PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
    // presigned URL을 반환하도록 설정한다
    when(presignedRequest.url()).thenReturn(new java.net.URL("https://s3.amazonaws.com/upload"));
    // s3Presigner.presignPutObject() 호출 시 위 모킹 객체를 반환하도록 설정한다
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenReturn(presignedRequest);

    // When: presign 요청을 실행한다
    PresignResponse response = s3Service.requestPresign(request);

    // Then: uploadUrl이 S3 presigned URL과 일치해야 한다
    assertThat(response.uploadUrl()).isEqualTo("https://s3.amazonaws.com/upload");
    // Then: finalUrl이 CloudFront 도메인으로 시작해야 한다
    assertThat(response.finalUrl()).startsWith("https://files.test.com/");
    // Then: finalUrl이 .pdf로 끝나야 한다
    assertThat(response.finalUrl()).endsWith(".pdf");
    // Then: isPdf 플래그가 true여야 한다
    assertThat(response.isPdf()).isTrue();
  }

  // 보장: 비PDF 파일이 presign 경로로 진입하지 못해 변환 없이 S3에 원본이 올라가는 사고를 방지한다
  @Test
  @DisplayName("비PDF presign 요청 시 EXTENSION_INVALID 예외를 던진다")
  void requestPresign_nonPdfFile_throwsException() {
    // Given: PPTX 파일에 대한 presign 요청 DTO를 생성한다
    // presign은 PDF 전용이므로 PPTX는 거부되어야 한다
    PresignRequest request =
        new PresignRequest(
            "presentation.pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            1024L);

    // When & Then: presign 호출 시 EXTENSION_INVALID 예외가 발생하는지 검증한다
    // 비PDF 파일은 /s3/upload-non-pdf 엔드포인트를 사용해야 한다
    assertThatThrownBy(() -> s3Service.requestPresign(request))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.EXTENSION_INVALID.getMessage());
  }

  // 보장: multipart 업로드 → PDF 변환 → S3 업로드 → CloudFront URL 반환의 전체 플로우가 연결된다
  @Test
  @DisplayName("비PDF 파일 업로드 시 PDF 변환 후 S3에 업로드하고 CloudFront URL을 반환한다")
  void uploadNonPdfFile_pptxFile_convertsAndUploads() throws IOException {
    // Given: 테스트 리소스에서 sample.pptx를 읽어 MockMultipartFile을 생성한다
    // MockMultipartFile: Spring의 MultipartFile 테스트 구현체 (실제 HTTP 요청 없이 파일 업로드를 시뮬레이션)
    MockMultipartFile file =
        new MockMultipartFile(
            "file", // 파라미터명 (컨트롤러의 @RequestParam("file")과 매칭)
            "presentation.pptx", // 원본 파일명
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // MIME
            loadResource("sample.pptx")); // 파일 내용을 리소스에서 로드

    // ConvertService.convertToPdf()가 반환할 임시 PDF 파일을 준비한다
    // 실제 S3ServiceImpl.finally에서 이 파일을 삭제하므로, 리소스 원본 대신 임시 복사본을 사용한다
    Path convertedPdf = Files.createTempFile("converted", ".pdf");
    try (InputStream is = loadResource("converted.pdf")) {
      // 리소스의 converted.pdf 내용을 임시 파일에 복사한다
      Files.copy(is, convertedPdf, StandardCopyOption.REPLACE_EXISTING);
    }
    // convertToPdf(아무 Path)가 호출되면 위 임시 PDF 경로를 반환하도록 설정한다
    when(convertService.convertToPdf(any(Path.class))).thenReturn(convertedPdf);

    // S3 putObject 호출 시 성공 응답을 반환하도록 모킹한다
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    // When: 비PDF 파일 업로드를 실행한다
    // 내부 플로우: 임시파일 저장 → convertToPdf → S3 putObject → CloudFront URL 생성
    PresignResponse response = s3Service.uploadNonPdfFile(file);

    // Then: finalUrl이 CloudFront 도메인으로 시작하고 .pdf로 끝나야 한다
    assertThat(response.finalUrl()).startsWith("https://files.test.com/");
    assertThat(response.finalUrl()).endsWith(".pdf");
    // Then: presign URL은 null이어야 한다 (서버가 직접 업로드했으므로 프론트엔드용 URL이 없음)
    assertThat(response.uploadUrl()).isNull();
    // Then: isPdf 플래그가 true여야 한다
    assertThat(response.isPdf()).isTrue();

    // Then: ConvertService.convertToPdf()가 호출되었는지 검증한다
    verify(convertService).convertToPdf(any(Path.class));
    // Then: S3Client.putObject()가 호출되어 변환된 PDF가 업로드되었는지 검증한다
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }
}
