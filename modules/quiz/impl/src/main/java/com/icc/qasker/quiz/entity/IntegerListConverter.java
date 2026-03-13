package com.icc.qasker.quiz.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/** referenced_page 테이블을 대체 — 페이지 번호 목록을 JSON으로 저장. */
@Converter
public class IntegerListConverter implements AttributeConverter<List<Integer>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<Integer> attribute) {
    if (attribute == null) return "[]";
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to convert integer list to JSON", e);
    }
  }

  @Override
  public List<Integer> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) return new ArrayList<>();
    try {
      return MAPPER.readValue(dbData, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to convert JSON to integer list", e);
    }
  }
}
