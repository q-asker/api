package com.icc.qasker.quizset.mapper;

import com.icc.qasker.quizset.dto.feresponse.AcceptedAnswer;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 엔티티 {@link com.icc.qasker.quizset.entity.AcceptedAnswer} → 응답 DTO {@link AcceptedAnswer} 변환. 응시
 * 조회와 히스토리 상세가 동일 데이터를 같은 형태로 내려주도록 공용화한다(FR-006). null(허용변형 없는 문항)은 그대로 보존해 클라이언트 폴백 채점을 유도한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptedAnswerMapper {

  public static List<AcceptedAnswer> toResponse(
      List<com.icc.qasker.quizset.entity.AcceptedAnswer> source) {
    if (source == null) {
      return null;
    }
    return source.stream().map(a -> new AcceptedAnswer(a.answer(), a.accepted())).toList();
  }
}
