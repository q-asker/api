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

  /** appliedInstruction 포함 스키마 (사용자 지시사항 있을 때) */
  private static final String WITH_INSTRUCTION =
      new BeanOutputConverter<>(GeminiResponse.class).getJsonSchema();

  /** appliedInstruction 제외 스키마 (사용자 지시사항 없을 때) */
  private static final String WITHOUT_INSTRUCTION = stripAppliedInstruction(WITH_INSTRUCTION);

  /** Phase 1(문제) 스키마 — 선지별 해설(explanation)을 제외한다. */
  private static final String PROBLEM_WITH_INSTRUCTION = stripExplanation(WITH_INSTRUCTION);

  private static final String PROBLEM_WITHOUT_INSTRUCTION = stripExplanation(WITHOUT_INSTRUCTION);

  /** customInstruction 유무에 따라 적절한 스키마를 반환한다. */
  public static String forInstruction(String customInstruction) {
    if (customInstruction == null || customInstruction.isBlank()) {
      return WITHOUT_INSTRUCTION;
    }
    return WITH_INSTRUCTION;
  }

  /** Phase 1(문제 생성) 스키마를 반환한다. 선지별 해설을 스키마에서 제거해 Gemini가 해설을 생성하지 않도록 한다(해설은 Phase 2에서 별도 생성). */
  public static String forProblemPhase(String customInstruction) {
    if (customInstruction == null || customInstruction.isBlank()) {
      return PROBLEM_WITHOUT_INSTRUCTION;
    }
    return PROBLEM_WITH_INSTRUCTION;
  }

  /** JSON 스키마에서 선지별 해설(explanation) 프로퍼티를 제거한다. */
  private static String stripExplanation(String schema) {
    try {
      ObjectMapper om = new ObjectMapper();
      JsonNode root = om.readTree(schema);
      stripFieldRecursive(root, "explanation");
      return om.writeValueAsString(root);
    } catch (JacksonException e) {
      return schema;
    }
  }

  /** JSON 스키마에서 appliedInstruction 프로퍼티와 required 항목을 제거한다. */
  private static String stripAppliedInstruction(String schema) {
    try {
      ObjectMapper om = new ObjectMapper();
      JsonNode root = om.readTree(schema);
      stripFieldRecursive(root, "appliedInstruction");
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
