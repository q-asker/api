package com.icc.qasker.quizset.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.entity.Selection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListConverterTest {

  @Nested
  class SelectionConverter {

    private final SelectionListConverter converter = new SelectionListConverter();

    @Test
    @DisplayName("null 속성은 '[]' 문자열로 변환된다")
    void null_attribute_to_empty_array() {
      assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    @DisplayName("null/blank DB 값은 빈 리스트로 변환된다")
    void blank_db_to_empty_list() {
      assertThat(converter.convertToEntityAttribute(null)).isEmpty();
      assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
    }

    @Test
    @DisplayName("round-trip 후 동등하다")
    void round_trip() {
      List<Selection> original =
          List.of(new Selection("a", "ea", true), new Selection("b", "eb", false));
      String db = converter.convertToDatabaseColumn(original);
      assertThat(converter.convertToEntityAttribute(db)).isEqualTo(original);
    }

    @Test
    @DisplayName("파싱 실패 시 FAIL_CONVERT")
    void parse_failure() {
      assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.FAIL_CONVERT.getMessage());
    }
  }

  @Nested
  class IntegerConverter {

    private final IntegerListConverter converter = new IntegerListConverter();

    @Test
    @DisplayName("null 속성은 '[]' 문자열로 변환된다")
    void null_attribute_to_empty_array() {
      assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    @DisplayName("null/blank DB 값은 빈 리스트로 변환된다")
    void blank_db_to_empty_list() {
      assertThat(converter.convertToEntityAttribute(null)).isEmpty();
      assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    @DisplayName("round-trip 후 동등하다")
    void round_trip() {
      List<Integer> original = List.of(1, 3, 5);
      String db = converter.convertToDatabaseColumn(original);
      assertThat(converter.convertToEntityAttribute(db)).isEqualTo(original);
    }

    @Test
    @DisplayName("파싱 실패 시 FAIL_CONVERT")
    void parse_failure() {
      assertThatThrownBy(() -> converter.convertToEntityAttribute("{bad}"))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.FAIL_CONVERT.getMessage());
    }
  }
}
