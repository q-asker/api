package com.icc.qasker.quizset.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 엔티티 → 응답 DTO 허용답안 변환(AcceptedAnswerMapper) 검증. 응시 조회·히스토리 상세가 이 공용 변환을 거쳐 동일 형태로 내려가므로(FR-006),
 * 변환의 값 보존과 null 보존을 확인한다.
 */
class AcceptedAnswerMapperTest {

  @Test
  @DisplayName("엔티티 허용답안을 응답 DTO로 값 손실 없이 변환한다")
  void maps_values() {
    List<com.icc.qasker.quizset.entity.AcceptedAnswer> source =
        List.of(
            new com.icc.qasker.quizset.entity.AcceptedAnswer("감수분열", List.of("meiosis")),
            new com.icc.qasker.quizset.entity.AcceptedAnswer("체세포분열", List.of()));

    List<com.icc.qasker.quizset.dto.feresponse.AcceptedAnswer> result =
        AcceptedAnswerMapper.toResponse(source);

    assertThat(result)
        .extracting(
            com.icc.qasker.quizset.dto.feresponse.AcceptedAnswer::answer,
            com.icc.qasker.quizset.dto.feresponse.AcceptedAnswer::accepted)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("감수분열", List.of("meiosis")),
            org.assertj.core.groups.Tuple.tuple("체세포분열", List.of()));
  }

  @Test
  @DisplayName("null(허용변형 없는 문항)은 null로 보존해 클라이언트 폴백을 유도한다")
  void preserves_null() {
    assertThat(AcceptedAnswerMapper.toResponse(null)).isNull();
  }
}
