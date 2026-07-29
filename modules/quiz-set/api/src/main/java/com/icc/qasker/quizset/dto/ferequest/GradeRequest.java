package com.icc.qasker.quizset.dto.ferequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * REAL_BLANK 무상태 채점 요청. 사용자가 제출한 raw 입력만 담고, 판정은 서버가 수행한다(채점 SSOT). 결과·해설 화면이 로그인 여부와 무관하게 이 엔드포인트를
 * 호출한다.
 */
public record GradeRequest(
    @NotBlank(message = "problemSetId가 null입니다.") String problemSetId,
    @NotNull(message = "answers가 null입니다.") List<GradeAnswer> answers) {

  /** {@code textAnswer}는 다중 빈칸이면 U+001F로 결합된 직렬화 문자열, 단일 빈칸이면 raw 입력이다. */
  public record GradeAnswer(int number, String textAnswer) {}
}
