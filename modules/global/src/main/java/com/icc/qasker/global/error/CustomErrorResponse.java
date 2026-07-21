package com.icc.qasker.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class CustomErrorResponse {

  // 안정적 오류 식별 코드. null이면 응답 body에서 생략(기존 응답 하위호환).
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String code;

  private final String message;

  public CustomErrorResponse(String message) {
    this(null, message);
  }

  public CustomErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
  }
}
