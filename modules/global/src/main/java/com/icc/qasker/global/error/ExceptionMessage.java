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
  FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 제한을 초과했습니다."),
  UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
  CONVERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 변환에 실패했습니다."),
  CONVERT_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "파일 변환 시간이 초과되었습니다."),
  LIBRE_OFFICE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "LibreOffice를 찾을 수 없습니다."),

  // ## QUIZ ISSUE
  // ExplanationService.java
  PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제를 찾을 수 없습니다."),

  // ## AI ISSUE
  // GenerationService.java
  AI_SERVER_COMMUNICATION_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "AI 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
  AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "문제 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
  AI_DUPLICATED_GENERATION(HttpStatus.BAD_REQUEST, "이미 생성중인 퀴즈입니다."),
  AI_SERVER_RESPONSE_ERROR(HttpStatus.BAD_REQUEST, "퀴즈 생성에 실패했습니다"),
  AI_SERVER_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "AI 서버와의 타임아웃이 발생했습니다"),

  // ## FE ISSUE
  PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
  INVALID_URL_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 url입니다"),

  // ## AUTH ISSUE
  USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),

  // JWT
  TOKEN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로그인에 실패했습니다."),

  // Auth
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  NOT_ENOUGH_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "재로그인이 필요합니다."),

  // ## POST-ISSUE
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
  ALREADY_ANSWERED(HttpStatus.FORBIDDEN, "답변이 달린 글은 수정 및 삭제가 불가능합니다.");
  private final HttpStatus httpStatus;
  private final String message;
}
