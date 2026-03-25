package com.icc.qasker.quizhistory.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.dto.ferequest.UserAnswer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class UserAnswerConverter implements AttributeConverter<List<UserAnswer>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<UserAnswer> attribute) {
    if (attribute == null) {
      return "[]";
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }

  @Override
  public List<UserAnswer> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return List.copyOf(MAPPER.readValue(dbData, new TypeReference<>() {}));
    } catch (JsonProcessingException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }
}
