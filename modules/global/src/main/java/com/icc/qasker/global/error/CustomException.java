package com.icc.qasker.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

  private final HttpStatus httpStatus;
  private final String message;
  private final String context;

  public CustomException(ExceptionMessage exceptionMessage) {
    super(exceptionMessage.getMessage());
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = null;
  }

  public CustomException(HttpStatus httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
    this.message = message;
    this.context = null;
  }

  public CustomException(ExceptionMessage exceptionMessage, String context) {
    super(exceptionMessage.getMessage());
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = context;
  }

  public CustomException(ExceptionMessage exceptionMessage, Throwable cause) {
    super(exceptionMessage.getMessage(), cause);
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = null;
  }

  public CustomException(ExceptionMessage exceptionMessage, String context, Throwable cause) {
    super(exceptionMessage.getMessage(), cause);
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = context;
  }
}
