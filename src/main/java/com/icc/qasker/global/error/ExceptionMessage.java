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
    EXTENSION_INVALID(HttpStatus.BAD_REQUEST, "허용하지 않는 확장자입니다."),

    // ExplanationService.java
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제를 찾을 수 없습니다."),
    NULL_ANSWER_INPUT(HttpStatus.BAD_REQUEST, "해당 문제에 대한 정답이 없습니다."),
    INVALID_CORRECT_ANSWER(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 정답이 존재하지 않습니다."),

    // GenerationService.java
    // AI ISSUE
    AI_SERVER_TIMEOUT(HttpStatus.REQUEST_TIMEOUT,"AI서버 응답 시간이 초과되었습니다."),
    AI_SERVER_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "AI서버에 연결할 수 없습니다."),
    AI_SERVER_RESPONSE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI서버에서 오류가 발생했습니다."),

    NULL_AI_RESPONSE(HttpStatus.BAD_REQUEST,"AI 응답이 null입니다."),
    EMPTY_QUIZ_LIST(HttpStatus.BAD_REQUEST,"퀴즈 목록이 비어있습니다."),
    NULL_QUIZ(HttpStatus.BAD_REQUEST,"퀴즈가 null입니다."),
    INVALID_QUIZ_TITLE(HttpStatus.BAD_REQUEST,"유효하지 않은 지문입니다."),
    INVALID_SELECTIONS(HttpStatus.BAD_REQUEST,"유효하지 않은 선택지들입니다."),
    INVALID_CORRECT_ANSWER_INDEX(HttpStatus.BAD_REQUEST,"유효하지 않은 정답입니다."),
    INVALID_EXPLANATION(HttpStatus.BAD_REQUEST,"유효하지 않은 해설입니다."),

    // FE ISSUE
    NULL_GENERATION_REQUEST(HttpStatus.BAD_REQUEST, "생성 요청이 null입니다."),
    INVALID_URL(HttpStatus.BAD_REQUEST,"URL이 없습니다."),
    INVALID_QUESTION_COUNT(HttpStatus.BAD_REQUEST,"문제 개수가 유효하지 않습니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST,"난이도가 유효하지 않습니다."),

    // Default
    DEFAULT_ERROR(HttpStatus.BAD_REQUEST,"에러가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

}
