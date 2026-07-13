package com.icc.qasker.oci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.oci.properties.CdnProperties;
import com.icc.qasker.oci.properties.OciObjectStorageProperties;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriUtils;

class OciObjectStorageServiceImplTest {

  private static final String IMAGE_BASE_URL = "https://img.test.com";
  private static final String FILE_BASE_URL = "https://files.test.com";

  private OciObjectStorageServiceImpl service;
  private UploadManager uploadManager;

  @BeforeEach
  void setUp() {
    uploadManager = mock(UploadManager.class);
    when(uploadManager.upload(any(UploadRequest.class))).thenReturn(mock(UploadResponse.class));

    OciObjectStorageProperties ociProperties =
        new OciObjectStorageProperties(
            "test-namespace",
            "test-image-bucket",
            "test-pdf-bucket",
            "ap-chuncheon-1",
            "~/.oci/config",
            "DEFAULT");

    CdnProperties cdnProperties = new CdnProperties(IMAGE_BASE_URL, FILE_BASE_URL);

    service =
        new OciObjectStorageServiceImpl(
            cdnProperties, ociProperties, uploadManager, new SimpleMeterRegistry());
  }

  // ── 업로드 URL 계약 + objectName 규칙 ─────────────────────────────

  @Test
  @DisplayName("이미지 업로드 시 imageBaseUrl 기반 images/<UUID><ext> URL을 반환한다")
  void uploadImage_returnsImageCdnUrlWithObjectNameRule() {
    byte[] content = "dummy image content".getBytes();
    InputStream inputStream = new ByteArrayInputStream(content);

    String cdnUrl = service.uploadImage(inputStream, content.length, "image/png", "test.png");

    assertThat(cdnUrl).startsWith(IMAGE_BASE_URL + "/images/");
    assertThat(cdnUrl).endsWith(".png");

    PutObjectRequest captured = captureRequest();
    assertThat(captured.getBucketName()).isEqualTo("test-image-bucket");
    assertThat(captured.getContentType()).isEqualTo("image/png");
    // objectName == URL 접미부(images/<UUID>.png)와 일치
    assertThat(captured.getObjectName())
        .isEqualTo(cdnUrl.substring((IMAGE_BASE_URL + "/").length()));
    assertThat(captured.getObjectName()).matches("images/[0-9a-fA-F-]{36}\\.png");
  }

  @Test
  @DisplayName("PDF 업로드 시 fileBaseUrl 기반 <UUID>.pdf URL을 반환한다")
  void uploadPdf_returnsFileCdnUrlWithObjectNameRule() throws IOException {
    Path pdfFile = Files.createTempFile("test", ".pdf");
    Files.writeString(pdfFile, "dummy pdf content");
    try {
      String cdnUrl = service.uploadPdf(pdfFile, "document.pdf");

      assertThat(cdnUrl).startsWith(FILE_BASE_URL + "/");
      assertThat(cdnUrl).endsWith(".pdf");

      PutObjectRequest captured = captureRequest();
      assertThat(captured.getBucketName()).isEqualTo("test-pdf-bucket");
      assertThat(captured.getContentType()).isEqualTo("application/pdf");
      assertThat(captured.getObjectName())
          .isEqualTo(cdnUrl.substring((FILE_BASE_URL + "/").length()));
      assertThat(captured.getObjectName()).matches("[0-9a-fA-F-]{36}\\.pdf");
    } finally {
      Files.deleteIfExists(pdfFile);
    }
  }

  // ── opcMeta 인코딩 ──────────────────────────────────────────────

  @Test
  @DisplayName("이미지 업로드 시 한글 파일명이 UriUtils.encode로 인코딩되어 opcMeta에 실린다")
  void uploadImage_encodesKoreanFileNameIntoOpcMeta() {
    String originalFileName = "한글 파일&이름.png";
    byte[] content = "x".getBytes();

    service.uploadImage(
        new ByteArrayInputStream(content), content.length, "image/png", originalFileName);

    PutObjectRequest captured = captureRequest();
    String expected = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);
    assertThat(captured.getOpcMeta()).containsEntry("original-filename", expected);
    // 인코딩되었으므로 원문 공백/특수문자가 그대로 남아있지 않다
    assertThat(expected).doesNotContain(" ");
  }

  @Test
  @DisplayName("PDF 업로드 시 한글 파일명이 UriUtils.encode로 인코딩되어 opcMeta에 실린다")
  void uploadPdf_encodesKoreanFileNameIntoOpcMeta() throws IOException {
    String originalFileName = "보고서 최종본.pdf";
    Path pdfFile = Files.createTempFile("test", ".pdf");
    Files.writeString(pdfFile, "dummy");
    try {
      service.uploadPdf(pdfFile, originalFileName);

      PutObjectRequest captured = captureRequest();
      String expected = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);
      assertThat(captured.getOpcMeta()).containsEntry("original-filename", expected);
    } finally {
      Files.deleteIfExists(pdfFile);
    }
  }

  // ── markSupported 분기 ──────────────────────────────────────────

  @Test
  @DisplayName("markSupported=false 스트림은 BufferedInputStream으로 래핑된다")
  void uploadImage_wrapsNonMarkSupportedStream() {
    InputStream nonMark =
        new FilterInputStream(new ByteArrayInputStream("x".getBytes())) {
          @Override
          public boolean markSupported() {
            return false;
          }
        };

    service.uploadImage(nonMark, 1, "image/png", "a.png");

    PutObjectRequest captured = captureRequest();
    assertThat(captured.getPutObjectBody()).isInstanceOf(BufferedInputStream.class);
  }

  @Test
  @DisplayName("markSupported=true 스트림은 래핑 없이 그대로 전달된다")
  void uploadImage_keepsMarkSupportedStream() {
    ByteArrayInputStream markSupported = new ByteArrayInputStream("x".getBytes());

    service.uploadImage(markSupported, 1, "image/png", "a.png");

    PutObjectRequest captured = captureRequest();
    assertThat(captured.getPutObjectBody()).isSameAs(markSupported);
  }

  // ── 헬퍼 ────────────────────────────────────────────────────────

  private PutObjectRequest captureRequest() {
    ArgumentCaptor<UploadRequest> captor = ArgumentCaptor.forClass(UploadRequest.class);
    verify(uploadManager).upload(captor.capture());
    return extractPutObjectRequest(captor.getValue());
  }

  private static PutObjectRequest extractPutObjectRequest(UploadRequest uploadRequest) {
    try {
      Field field = UploadRequest.class.getDeclaredField("putObjectRequest");
      field.setAccessible(true);
      return (PutObjectRequest) field.get(uploadRequest);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("UploadRequest 내부 PutObjectRequest 추출 실패", e);
    }
  }
}
