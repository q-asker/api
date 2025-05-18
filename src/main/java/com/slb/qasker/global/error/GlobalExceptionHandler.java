package com.slb.qasker.global.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(
        CustomException customException) {

        return ResponseEntity.status(customException.getHttpStatus())
            .body(new CustomErrorResponse(customException.getMessage()));
    }
}