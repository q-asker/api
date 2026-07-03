package com.icc.qasker.quizset.converter;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.entity.Selection;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Converter
public class SelectionListConverter implements AttributeConverter<List<Selection>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<Selection> attribute) {
    if (attribute == null) {
      return "[]";
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JacksonException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }

  @Override
  public List<Selection> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return List.copyOf(MAPPER.readValue(dbData, new TypeReference<>() {}));
    } catch (JacksonException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }
}
