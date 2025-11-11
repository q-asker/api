package com.icc.qasker.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<com.icc.qasker.global.error.CustomErrorResponse> handleCustomException(
        CustomException customException) {

        return ResponseEntity.status(customException.getHttpStatus())
            .body(
                new com.icc.qasker.global.error.CustomErrorResponse(customException.getMessage()));
    }
}