package com.icc.qasker.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionMessage {
    // ## Default ISSUE
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "에러가 발생했습니다."),

    // ## AWS ISSUE
    OUT_OF_FILE_SIZE(HttpStatus.BAD_REQUEST, "허용되지 않은 파일 크기입니다."),
    NO_FILE_UPLOADED(HttpStatus.BAD_REQUEST, "파일이 업로드되지 않았습니다."),
    FILE_NAME_NOT_EXIST(HttpStatus.BAD_REQUEST, "파일 이름이 존재하지 않습니다"),
    FILE_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "파일 이름이 깁니다"),
    EXTENSION_NOT_EXIST(HttpStatus.BAD_REQUEST, "확장자가 존재하지 않습니다"),
    EXTENSION_INVALID(HttpStatus.BAD_REQUEST, "허용하지 않는 확장자입니다."),
    FILE_NOT_FOUND_ON_S3(HttpStatus.NOT_FOUND, "파일이 존재하지 않습니다. (기존파일의 경우 24시간이 지나 삭제되었을 수 있습니다)"),

    // ## QUIZ ISSUE
    // ExplanationService.java
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제를 찾을 수 없습니다."),
    NULL_ANSWER_INPUT(HttpStatus.BAD_REQUEST, "해당 문제에 대한 정답이 없습니다."),
    INVALID_CORRECT_ANSWER(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 정답이 존재하지 않습니다."),

    // ## AI ISSUE
    // GenerationService.java
    AI_SERVER_COMMUNICATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
        "AI 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI서버 응답 시간이 초과되었습니다."),
    AI_SERVER_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "AI서버에 연결할 수 없습니다."),
    AI_SERVER_RESPONSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI서버에서 오류가 발생했습니다."),
    AI_SERVER_TO_MANY_REQUEST(HttpStatus.TOO_MANY_REQUESTS,
        "서버가 생성요청 한도에 도달했습니다. 문제 개수를 줄이거나 1분 뒤 다시 시도해주세요."),
    NULL_AI_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "AI 응답이 null입니다."),
    INVALID_AI_RESPONSE(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 AI의 응답입니다."),


    // ## FE ISSUE
    // NULL_GENERATION_REQUEST(HttpStatus.BAD_REQUEST, "생성 요청이 null입니다."),
    // INVALID_FE_REQUEST(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 요청입니다."),
    PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
    INVALID_URL_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 url입니다"),
    INVALID_QUIZ_COUNT_REQUEST(HttpStatus.BAD_REQUEST, "quizCount는 5배수입니다."),
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "페이지 수가 100 이상입니다."),

    // ## AUTH ISSUE
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 ID입니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),

    // JWT
    TOKEN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "토큰 생성 중 서버 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    NOT_ENOUGH_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 부족합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
