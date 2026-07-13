package com.icc.qasker.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoOpConvertServiceTest {

  private final NoOpConvertService convertService = new NoOpConvertService();

  // 보장: LibreOffice 비활성 환경(CI/test)에서 변환 요청이 명확한 예외로 차단된다
  @Test
  @DisplayName("변환 요청 시 UnsupportedOperationException을 던진다")
  void convertToPdf_throwsUnsupportedOperation() {
    assertThatThrownBy(() -> convertService.convertToPdf(Path.of("sample.pptx")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
