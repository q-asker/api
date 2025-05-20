package com.icc.qasker.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionMessage {

    NO_FILE_UPLOADED(HttpStatus.BAD_REQUEST, "파일이 업로드되지 않았습니다."),
    FILE_NAME_NOT_EXIST(HttpStatus.BAD_REQUEST, "파일 이름이 존재하지 않습니다"),
    FILE_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "파일 이름이 깁니다"),
    EXTENSION_NOT_EXIST(HttpStatus.BAD_REQUEST, "확장자가 존재하지 않습니다"),
    EXTENSION_INVALID(HttpStatus.BAD_REQUEST, "허용하지 않는 확장자입니다.");

    private final HttpStatus httpStatus;
    private final String message;

}
