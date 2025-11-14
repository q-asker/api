package com.icc.qasker.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(
        CustomException customException) {
        log.error(customException.getMessage());

        return ResponseEntity.status(customException.getHttpStatus())
            .body(
                new CustomErrorResponse(customException.getMessage()));
    }
}