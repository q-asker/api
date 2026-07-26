package com.icc.qasker.quizset.converter;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.entity.AcceptedAnswer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AcceptedAnswer} 목록을 accepted_answers TEXT 컬럼에 JSON으로 직렬화한다. SelectionListConverter와 동일
 * 패턴이나, 허용답안이 없는 문항(이 기능 이전 생성분 포함)은 null을 그대로 보존한다 — null이면 클라이언트가 완전일치+표기정규화 폴백으로 채점한다.
 */
@Converter
public class AcceptedAnswerListConverter
    implements AttributeConverter<List<AcceptedAnswer>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<AcceptedAnswer> attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JacksonException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }

  @Override
  public List<AcceptedAnswer> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    try {
      return List.copyOf(MAPPER.readValue(dbData, new TypeReference<>() {}));
    } catch (JacksonException e) {
      throw new CustomException(ExceptionMessage.FAIL_CONVERT);
    }
  }
}
