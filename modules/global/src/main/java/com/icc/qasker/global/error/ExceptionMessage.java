package com.icc.qasker.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionMessage {
    // ## Default ISSUE
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ## AWS ISSUE
    OUT_OF_FILE_SIZE(HttpStatus.BAD_REQUEST, "허용되지 않은 파일 크기입니다."),
    NO_FILE_UPLOADED(HttpStatus.BAD_REQUEST, "파일이 업로드되지 않았습니다."),
    FILE_NAME_NOT_EXIST(HttpStatus.BAD_REQUEST, "파일 이름이 존재하지 않습니다"),
    FILE_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "파일 이름이 깁니다"),
    EXTENSION_NOT_EXIST(HttpStatus.BAD_REQUEST, "확장자가 존재하지 않습니다"),
    EXTENSION_INVALID(HttpStatus.BAD_REQUEST, "허용하지 않는 확장자입니다."),

    // ## QUIZ ISSUE
    // ExplanationService.java
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제를 찾을 수 없습니다."),

    // ## AI ISSUE
    // GenerationService.java
    AI_SERVER_COMMUNICATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
        "AI 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
        "문제 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AI_DUPLICATED_GENERATION(HttpStatus.BAD_REQUEST,
        "이미 생성중인 퀴즈입니다."),

    // ## FE ISSUE
    PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
    INVALID_URL_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 url입니다"),

    // ## AUTH ISSUE
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),

    // JWT
    TOKEN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로그인에 실패했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    NOT_ENOUGH_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
