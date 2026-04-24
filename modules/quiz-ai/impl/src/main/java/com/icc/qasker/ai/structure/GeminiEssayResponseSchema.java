package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;

/** ESSAY 전용 JSON 스키마. customInstruction 유무에 따라 appliedInstruction 필드를 포함/제외한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiEssayResponseSchema {

  private static final String WITH_INSTRUCTION =
      new BeanOutputConverter<>(GeminiEssayResponse.class).getJsonSchema();

  private static final String WITHOUT_INSTRUCTION = stripAppliedInstruction(WITH_INSTRUCTION);

  public static String forInstruction(String customInstruction) {
    if (customInstruction == null || customInstruction.isBlank()) {
      return WITHOUT_INSTRUCTION;
    }
    return WITH_INSTRUCTION;
  }

  private static String stripAppliedInstruction(String schema) {
    try {
      ObjectMapper om = new ObjectMapper();
      JsonNode root = om.readTree(schema);
      stripFieldRecursive(root, "appliedInstruction");
      return om.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      return schema;
    }
  }

  private static void stripFieldRecursive(JsonNode node, String fieldName) {
    if (!node.isObject()) return;

    ObjectNode obj = (ObjectNode) node;

    if (obj.has("properties") && obj.get("properties").has(fieldName)) {
      ((ObjectNode) obj.get("properties")).remove(fieldName);

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

    obj.fields()
        .forEachRemaining(
            entry -> {
              if (entry.getValue().isObject()) {
                stripFieldRecursive(entry.getValue(), fieldName);
              }
            });
  }
}
