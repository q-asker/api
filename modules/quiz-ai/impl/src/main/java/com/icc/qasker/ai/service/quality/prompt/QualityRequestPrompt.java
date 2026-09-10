package com.icc.qasker.ai.service.quality.prompt;

import com.icc.qasker.ai.dto.QualityVerificationRequest;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** 검증 대상 문항을 담는 유저 프롬프트. 검증관이 문항 자체(+캐시에 실린 PDF 원문)만 보고 판정하도록 문항 필드만 싣는다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QualityRequestPrompt {

  public static String build(QualityVerificationRequest request, ObjectMapper objectMapper) {
    // 검증관은 문항 자체(+첨부 PDF 원문)만 보고 독립·비판적으로 판정한다.
    Map<String, Object> payload =
        Map.of(
            "question", nullToEmpty(request.question()),
            "selections", request.selections() == null ? List.of() : request.selections(),
            "modelAnswer", nullToEmpty(request.modelAnswer()),
            "customInstruction", nullToEmpty(request.customInstruction()),
            "appliedInstruction", nullToEmpty(request.appliedInstruction()),
            "priorRoundFeedback", nullToEmpty(request.priorFeedback()));
    return "# 검증 대상 문항\n" + serialize(payload, objectMapper);
  }

  private static String serialize(Object payload, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      return String.valueOf(payload);
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
