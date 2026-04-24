package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;

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

  /** customInstruction 유무에 따라 적절한 스키마를 반환한다. */
  public static String forInstruction(String customInstruction) {
    if (customInstruction == null || customInstruction.isBlank()) {
      return WITHOUT_INSTRUCTION;
    }
    return WITH_INSTRUCTION;
  }

  /** JSON 스키마에서 appliedInstruction 프로퍼티와 required 항목을 제거한다. */
  private static String stripAppliedInstruction(String schema) {
    try {
      ObjectMapper om = new ObjectMapper();
      JsonNode root = om.readTree(schema);
      stripFieldRecursive(root, "appliedInstruction");
      return om.writeValueAsString(root);
    } catch (JsonProcessingException e) {
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
    obj.fields()
        .forEachRemaining(
            entry -> {
              if (entry.getValue().isObject()) {
                stripFieldRecursive(entry.getValue(), fieldName);
              }
            });
  }
}
