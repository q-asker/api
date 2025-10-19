package com.icc.qasker.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String message;

    public CustomException(
        ExceptionMessage exceptionMessage
    ) {
        super(exceptionMessage.getMessage());
        this.httpStatus = exceptionMessage.getHttpStatus();
        this.message = exceptionMessage.getMessage();
    }
}