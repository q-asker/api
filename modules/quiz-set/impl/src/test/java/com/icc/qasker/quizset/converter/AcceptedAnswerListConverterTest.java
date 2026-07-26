package com.icc.qasker.quizset.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.AcceptedAnswer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AcceptedAnswerListConverter round-trip 및 null(허용변형 없는 문항 = 폴백 채점) 보존 검증. */
class AcceptedAnswerListConverterTest {

  private final AcceptedAnswerListConverter converter = new AcceptedAnswerListConverter();

  @Test
  @DisplayName("round-trip: 직렬화 후 역직렬화하면 동일한 허용답안 목록")
  void round_trip() {
    List<AcceptedAnswer> source =
        List.of(
            new AcceptedAnswer("감수분열", List.of("meiosis", "생식세포분열")),
            new AcceptedAnswer("체세포분열", List.of("mitosis")));

    String json = converter.convertToDatabaseColumn(source);
    List<AcceptedAnswer> restored = converter.convertToEntityAttribute(json);

    assertThat(restored).isEqualTo(source);
  }

  @Test
  @DisplayName("null은 null로 보존된다(허용변형 미생성 = 폴백 채점 대상)")
  void null_is_preserved() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("빈 문자열 컬럼은 null로 읽는다")
  void blank_reads_as_null() {
    assertThat(converter.convertToEntityAttribute("")).isNull();
    assertThat(converter.convertToEntityAttribute("   ")).isNull();
  }

  @Test
  @DisplayName("빈 accepted 목록도 손실 없이 round-trip")
  void empty_accepted_round_trip() {
    List<AcceptedAnswer> source = List.of(new AcceptedAnswer("투사", List.of()));

    String json = converter.convertToDatabaseColumn(source);

    assertThat(converter.convertToEntityAttribute(json)).isEqualTo(source);
  }
}
