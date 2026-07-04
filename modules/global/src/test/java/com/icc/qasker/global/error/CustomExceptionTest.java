package com.icc.qasker.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("CustomException 생성자 5종 계약 검증")
class CustomExceptionTest {

  @Test
  @DisplayName("ExceptionMessage 단일 생성자: httpStatus/message 매핑, context/cause 없음")
  void exceptionMessageOnly() {
    CustomException ex = new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("문제 세트를 찾을 수 없습니다.");
    assertThat(ex.getContext()).isNull();
    assertThat(ex.getCause()).isNull();
  }

  @Test
  @DisplayName("동적 메시지 생성자: 지정한 status/message 그대로, context/cause 없음")
  void dynamicStatusAndMessage() {
    CustomException ex = new CustomException(HttpStatus.BAD_REQUEST, "직접 지정 메시지");

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex.getMessage()).isEqualTo("직접 지정 메시지");
    assertThat(ex.getContext()).isNull();
    assertThat(ex.getCause()).isNull();
  }

  @Test
  @DisplayName("컨텍스트 생성자: context 보존, cause 없음")
  void withContext() {
    CustomException ex = new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND, "problemId=5");

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("해당 문제를 찾을 수 없습니다.");
    assertThat(ex.getContext()).isEqualTo("problemId=5");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  @DisplayName("원인 체이닝 생성자: cause 보존, context 없음")
  void withCause() {
    Throwable cause = new IllegalStateException("root");
    CustomException ex = new CustomException(ExceptionMessage.CONVERT_FAILED, cause);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(ex.getMessage()).isEqualTo("파일 변환에 실패했습니다.");
    assertThat(ex.getContext()).isNull();
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("컨텍스트 + 원인 생성자: context와 cause 모두 보존")
  void withContextAndCause() {
    Throwable cause = new RuntimeException("upload fail");
    CustomException ex =
        new CustomException(ExceptionMessage.FILE_UPLOAD_FAILED, "bucket=my-bucket", cause);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(ex.getMessage()).isEqualTo("파일 업로드에 실패했습니다.");
    assertThat(ex.getContext()).isEqualTo("bucket=my-bucket");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
