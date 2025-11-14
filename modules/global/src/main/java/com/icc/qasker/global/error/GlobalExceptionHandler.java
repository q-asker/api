package com.icc.qasker.global.error;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(
        CustomException customException) {
        log.error("custom error occurred", customException);

        return ResponseEntity.status(customException.getHttpStatus())
            .body(
                new CustomErrorResponse(customException.getMessage()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(
        CallNotPermittedException exception) {
        log.error("unexpected error occurred", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                new CustomErrorResponse(
                    ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR.getMessage()));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleCustomException(
        Exception exception) {
        log.error("unexpected error occurred", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                new CustomErrorResponse(ExceptionMessage.DEFAULT_ERROR.getMessage()));
    }
}