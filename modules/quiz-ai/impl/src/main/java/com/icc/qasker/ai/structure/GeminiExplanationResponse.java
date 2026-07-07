package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Phase 2(해설 생성) 응답 스키마. 직전 턴에서 생성·저장된 문항들의 선지별 해설을 문항 번호로 매칭해 반환한다.
 *
 * <p>selectionExplanations는 해당 문항의 <b>제시된 선지 순서와 동일한 인덱스</b>로 작성한다(저장 순서와 정합).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExplanationResponse(
    @JsonPropertyDescription("문항별 해설 목록") List<GeminiExplanation> explanations) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiExplanation(
      @JsonPropertyDescription("대상 문항 번호") int number,
      @JsonPropertyDescription("선지별 해설 — 제시된 선지 순서와 동일한 인덱스") List<String> selectionExplanations) {}

  /** Phase 2 응답 JSON 스키마 문자열. */
  public static String schema() {
    return new BeanOutputConverter<>(GeminiExplanationResponse.class).getJsonSchema();
  }
}
