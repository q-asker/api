package com.icc.qasker.global.error;

import com.icc.qasker.global.component.SlackNotifier;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final SlackNotifier slackNotifier;

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(
      CustomException customException) {
    log.error("Custom Error Occurred", customException);
    notifyError("Custom Error Occurred", customException);

    return ResponseEntity.status(customException.getHttpStatus())
        .body(new CustomErrorResponse(customException.getMessage()));
  }

  @ExceptionHandler(ClientSideException.class)
  public ResponseEntity<CustomErrorResponse> handleClientException(ClientSideException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new CustomErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(
      CallNotPermittedException exception) {
    log.error("Circuit Breaker Activated", exception);
    notifyError("Circuit Breaker Activated", exception);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new CustomErrorResponse(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<CustomErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException e) {
    return ResponseEntity.status(ExceptionMessage.FILE_SIZE_EXCEEDED.getHttpStatus())
        .body(new CustomErrorResponse(ExceptionMessage.FILE_SIZE_EXCEEDED.getMessage()));
  }

  @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
  public void handleSseException() {}

  @ExceptionHandler(Exception.class)
  public ResponseEntity<CustomErrorResponse> handleCustomException(Exception exception) {
    log.error("Unexpected Error Occurred", exception);
    notifyError("Unexpected Error Occurred", exception);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new CustomErrorResponse(ExceptionMessage.DEFAULT_ERROR.getMessage()));
  }

  private void notifyError(String message, Exception exception) {
    StringWriter sw = new StringWriter();
    exception.printStackTrace(new PrintWriter(sw));
    String stackTrace = sw.toString();
    // 스택트레이스가 너무 길면 잘라냄
    if (stackTrace.length() > 1500) {
      stackTrace = stackTrace.substring(0, 1500) + "\n...truncated";
    }
    String text =
        String.format(
            "*%s*\n```%s: %s```\n```%s```",
            message, exception.getClass().getSimpleName(), exception.getMessage(), stackTrace);
    slackNotifier.asyncNotifyError(text);
  }
}
