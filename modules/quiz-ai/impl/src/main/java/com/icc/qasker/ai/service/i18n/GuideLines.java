package com.icc.qasker.ai.service.i18n;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 유형별 생성 지침에 출력 언어 지시를 덧붙인다. 지침 본문은 한국어로 쓰여 있고, EN 요청이면 영어 출력 지시를 뒤에 붙인다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GuideLines {

  public static String withLanguage(String base, String language) {
    return switch (language) {
      case "EN" -> base + ENGLISH.content;
      case "KO" -> base;
      default -> throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    };
  }
}
