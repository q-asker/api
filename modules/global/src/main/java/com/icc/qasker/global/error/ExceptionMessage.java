package com.icc.qasker.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionMessage {
  // ## 공통 (global)
  DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
  FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 제한을 초과했습니다."),

  // ## 파일 업로드/변환 (aws, quiz-make, util)
  OUT_OF_FILE_SIZE(HttpStatus.BAD_REQUEST, "허용되지 않은 파일 크기입니다."),
  FILE_NAME_NOT_EXIST(HttpStatus.BAD_REQUEST, "파일 이름이 존재하지 않습니다"),
  FILE_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "파일 이름이 깁니다"),
  EXTENSION_NOT_EXIST(HttpStatus.BAD_REQUEST, "확장자가 존재하지 않습니다"),
  EXTENSION_INVALID(HttpStatus.BAD_REQUEST, "허용하지 않는 확장자입니다."),
  UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
  INVALID_URL_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 url입니다"),
  CONVERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 변환에 실패했습니다."),

  // ## 퀴즈 (quiz-set)
  PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제를 찾을 수 없습니다."),
  PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
  FAIL_CONVERT(HttpStatus.INTERNAL_SERVER_ERROR, "컨버팅에 실패했습니다."),

  // ## 퀴즈 히스토리 (quiz-history)
  QUIZ_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "퀴즈 히스토리를 찾을 수 없습니다."),

  // ## AI (quiz-ai, quiz-make)
  AI_SERVER_COMMUNICATION_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "AI 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
  AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "문제 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
  AI_DUPLICATED_GENERATION(HttpStatus.BAD_REQUEST, "이미 생성중인 퀴즈입니다."),
  AI_SERVER_RESPONSE_ERROR(HttpStatus.BAD_REQUEST, "퀴즈 생성에 실패했습니다"),
  AI_SERVER_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "AI 서버와의 타임아웃이 발생했습니다"),

  // ## 인증/인가 (auth)
  USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),
  TOKEN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로그인에 실패했습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  NOT_ENOUGH_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "재로그인이 필요합니다."),

  // ## 게시판 (board)
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
  ALREADY_ANSWERED(HttpStatus.FORBIDDEN, "답변이 달린 글은 수정 및 삭제가 불가능합니다."),

  // ## RATE LIMIT
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");

  private final HttpStatus httpStatus;
  private final String message;
}
