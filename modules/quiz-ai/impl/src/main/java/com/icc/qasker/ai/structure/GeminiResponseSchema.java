package com.icc.qasker.ai.structure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * customInstruction 유무에 따라 적절한 JSON 스키마를 제공한다.
 *
 * <p>지시사항이 없으면 appliedInstruction 필드를 스키마에서 제외하여 Gemini가 불필요한 값을 생성하지 않도록 한다. GeminiResponse 스키마
 * 하나에서 파생하므로 필드 정의 중복이 없다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiResponseSchema {

  /** appliedInstruction 포함 스키마 (사용자 지시사항 있을 때). GeminiSelection.acceptedAnswers도 포함. */
  private static final String WITH_INSTRUCTION =
      new BeanOutputConverter<>(GeminiResponse.class).getJsonSchema();

  /** appliedInstruction 제외 스키마 (사용자 지시사항 없을 때) */
  private static final String WITHOUT_INSTRUCTION =
      stripField(WITH_INSTRUCTION, "appliedInstruction");

  /** acceptedAnswers 제외 — 비 REAL_BLANK 기본값(다른 타입 생성에 새 필드 영향 없음). */
  private static final String WITH_INSTRUCTION_NO_ACCEPTED =
      stripField(WITH_INSTRUCTION, "acceptedAnswers");

  private static final String WITHOUT_INSTRUCTION_NO_ACCEPTED =
      stripField(WITHOUT_INSTRUCTION, "acceptedAnswers");

  /** customInstruction 유무에 따라 적절한 스키마를 반환한다(acceptedAnswers 제외 = 기본). */
  public static String forInstruction(String customInstruction) {
    return forInstruction(customInstruction, false);
  }

  /**
   * customInstruction 유무 + acceptedAnswers 포함 여부에 따라 스키마를 반환한다. {@code includeAcceptedAnswers}는
   * REAL_BLANK일 때만 true — 그 외에는 acceptedAnswers를 스키마에서 제거해 모델이 불필요한 값을 생성하지 않도록 한다.
   */
  public static String forInstruction(String customInstruction, boolean includeAcceptedAnswers) {
    boolean hasInstruction = customInstruction != null && !customInstruction.isBlank();
    if (includeAcceptedAnswers) {
      return hasInstruction ? WITH_INSTRUCTION : WITHOUT_INSTRUCTION;
    }
    return hasInstruction ? WITH_INSTRUCTION_NO_ACCEPTED : WITHOUT_INSTRUCTION_NO_ACCEPTED;
  }

  /** JSON 스키마에서 지정 프로퍼티와 required 항목을 재귀 제거한다. */
  private static String stripField(String schema, String fieldName) {
    try {
      ObjectMapper om = new ObjectMapper();
      JsonNode root = om.readTree(schema);
      stripFieldRecursive(root, fieldName);
      return om.writeValueAsString(root);
    } catch (JacksonException e) {
      // 스키마 조작 실패 시 원본 반환 (안전 폴백)
      return schema;
    }
  }

  /** 모든 $defs와 properties를 재귀 탐색하여 대상 필드를 제거한다. */
  private static void stripFieldRecursive(JsonNode node, String fieldName) {
    if (!node.isObject()) return;

    ObjectNode obj = (ObjectNode) node;

    // properties에서 필드 제거
    if (obj.has("properties") && obj.get("properties").has(fieldName)) {
      ((ObjectNode) obj.get("properties")).remove(fieldName);

      // required 배열에서도 제거
      if (obj.has("required") && obj.get("required").isArray()) {
        ArrayNode required = (ArrayNode) obj.get("required");
        ArrayNode filtered = required.arrayNode();
        for (JsonNode item : required) {
          if (!fieldName.equals(item.asText())) {
            filtered.add(item);
          }
        }
        obj.set("required", filtered);
      }
    }

    // 하위 노드 재귀 탐색 ($defs, properties 내부 등)
    obj.properties()
        .forEach(
            entry -> {
              if (entry.getValue().isObject()) {
                stripFieldRecursive(entry.getValue(), fieldName);
              }
            });
  }
}
