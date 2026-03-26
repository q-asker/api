package com.icc.qasker.ai.exception;

/** Gemini API 인프라 레벨 장애를 나타내는 예외. 파일 업로드, 캐시 생성/삭제 실패 등 서킷브레이커 카운트 대상이다. */
public class GeminiInfraException extends RuntimeException {

  public GeminiInfraException(String message, Throwable cause) {
    super(message, cause);
  }
}
