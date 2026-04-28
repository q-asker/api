package com.icc.qasker.global.error;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(
      CustomException customException) {
    if (customException.getHttpStatus().is4xxClientError()) {
      if (customException.getContext() != null) {
        log.warn(
            "[{}] {}", customException.getContext(), customException.getMessage(), customException);
      } else {
        log.warn("[클라이언트 오류] 요청 처리 중 예외 발생", customException);
      }
    } else {
      if (customException.getContext() != null) {
        log.error(
            "[{}] {}", customException.getContext(), customException.getMessage(), customException);
      } else {
        log.error("[서버 오류] 처리 중 예외 발생", customException);
      }
    }

    return ResponseEntity.status(customException.getHttpStatus())
        .body(new CustomErrorResponse(customException.getMessage()));
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(
      CallNotPermittedException exception) {
    log.error("[서킷 브레이커] AI 서버 차단 상태", exception);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new CustomErrorResponse(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<CustomErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException e) {
    log.warn("[파일 업로드 제한] 업로드 크기 초과", e);
    return ResponseEntity.status(ExceptionMessage.FILE_SIZE_EXCEEDED.getHttpStatus())
        .body(new CustomErrorResponse(ExceptionMessage.FILE_SIZE_EXCEEDED.getMessage()));
  }

  @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
  public void handleSseException() {}

  @ExceptionHandler(AsyncRequestTimeoutException.class)
  public ResponseEntity<CustomErrorResponse> handleAsyncRequestTimeoutException() {
    log.warn("[SSE 타임아웃] 비동기 요청 타임아웃 발생");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new CustomErrorResponse(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(Exception exception) {
    log.error("[예상치 못한 오류] 처리되지 않은 예외 발생", exception);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new CustomErrorResponse(ExceptionMessage.DEFAULT_ERROR.getMessage()));
  }
}
