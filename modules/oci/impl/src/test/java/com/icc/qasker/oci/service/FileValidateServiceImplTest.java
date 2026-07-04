package com.icc.qasker.oci.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.properties.CdnProperties;
import com.icc.qasker.oci.properties.FileValidationProperties;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileValidateServiceImplTest {

  private static final String IMAGE_BASE_URL = "https://img.test.com";
  private static final String FILE_BASE_URL = "https://files.test.com";
  private static final long MAX_FILE_SIZE = 100;
  private static final int MAX_FILE_NAME_LENGTH = 10;

  private FileValidateServiceImpl service;

  @BeforeEach
  void setUp() {
    CdnProperties cdnProperties = new CdnProperties(IMAGE_BASE_URL, FILE_BASE_URL);
    FileValidationProperties validationProperties =
        new FileValidationProperties(
            MAX_FILE_SIZE, MAX_FILE_NAME_LENGTH, "image/png,application/pdf");
    service = new FileValidateServiceImpl(cdnProperties, validationProperties);
  }

  // ── checkCdnUrlWithThrowing ─────────────────────────────────────

  @Test
  @DisplayName("imageBaseUrl로 시작하는 URL은 통과한다")
  void checkCdnUrl_imageBaseUrl_passes() {
    assertThatCode(() -> service.checkCdnUrlWithThrowing(IMAGE_BASE_URL + "/images/a.png"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fileBaseUrl로 시작하는 URL은 통과한다")
  void checkCdnUrl_fileBaseUrl_passes() {
    assertThatCode(() -> service.checkCdnUrlWithThrowing(FILE_BASE_URL + "/a.pdf"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("허용된 prefix가 아니면 INVALID_URL_REQUEST를 던진다")
  void checkCdnUrl_invalidPrefix_throws() {
    assertException(
        () -> service.checkCdnUrlWithThrowing("https://evil.com/a.png"),
        ExceptionMessage.INVALID_URL_REQUEST);
  }

  // ── validateFileWithThrowing ────────────────────────────────────

  @Test
  @DisplayName("정상 파일은 통과한다")
  void validateFile_valid_passes() {
    assertThatCode(() -> service.validateFileWithThrowing("a.png", 50, "image/png"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("파일 크기 초과 시 OUT_OF_FILE_SIZE를 던진다")
  void validateFile_oversized_throws() {
    assertException(
        () -> service.validateFileWithThrowing("a.png", MAX_FILE_SIZE + 1, "image/png"),
        ExceptionMessage.OUT_OF_FILE_SIZE);
  }

  @Test
  @DisplayName("파일명이 null이면 FILE_NAME_NOT_EXIST를 던진다")
  void validateFile_nullName_throws() {
    assertException(
        () -> service.validateFileWithThrowing(null, 50, "image/png"),
        ExceptionMessage.FILE_NAME_NOT_EXIST);
  }

  @Test
  @DisplayName("파일명이 최대 길이를 초과하면 FILE_NAME_TOO_LONG을 던진다")
  void validateFile_tooLongName_throws() {
    String longName = "a".repeat(MAX_FILE_NAME_LENGTH + 1);
    assertException(
        () -> service.validateFileWithThrowing(longName, 50, "image/png"),
        ExceptionMessage.FILE_NAME_TOO_LONG);
  }

  @Test
  @DisplayName("contentType이 null이면 EXTENSION_INVALID를 던진다")
  void validateFile_nullContentType_throws() {
    assertException(
        () -> service.validateFileWithThrowing("a.png", 50, null),
        ExceptionMessage.EXTENSION_INVALID);
  }

  @Test
  @DisplayName("허용되지 않은 확장자면 EXTENSION_INVALID를 던진다")
  void validateFile_disallowedContentType_throws() {
    assertException(
        () -> service.validateFileWithThrowing("a.gif", 50, "image/gif"),
        ExceptionMessage.EXTENSION_INVALID);
  }

  private void assertException(ThrowingCallable callable, ExceptionMessage message) {
    assertThatThrownBy(callable)
        .isInstanceOf(CustomException.class)
        .hasMessage(message.getMessage());
  }
}
