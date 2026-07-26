package com.icc.qasker.quizset.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizset.entity.AcceptedAnswer;
import com.icc.qasker.quizset.entity.Selection;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProblemMapper.buildAcceptedAnswers 단위 테스트 — 정답 content 콤마 토큰을 모범답안(answer)으로 삼아 허용변형과 짝짓는 조립 로직
 * 검증. 다중 빈칸 정렬(G)·폴백(F)이 주 대상.
 */
class ProblemMapperTest {

  private static Selection correct(String content) {
    return new Selection(content, null, true);
  }

  private static Selection wrong(String content) {
    return new Selection(content, null, false);
  }

  @Test
  @DisplayName("단일 빈칸: 정답 토큰이 answer, 허용변형이 accepted로 조립된다")
  void single_blank() {
    List<Selection> selections = List.of(correct("투사"), wrong("합리화"));
    List<AcceptedAnswer> result =
        ProblemMapper.buildAcceptedAnswers(selections, List.of(List.of("projection")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).answer()).isEqualTo("투사");
    assertThat(result.get(0).accepted()).containsExactly("projection");
  }

  @Test
  @DisplayName("G 다중 빈칸: answer는 정답 콤마 순서대로, accepted는 같은 순서로 매칭된다")
  void multi_blank_ordering() {
    List<Selection> selections = List.of(correct("감수분열, 체세포분열"), wrong("체세포분열, 감수분열"));
    List<AcceptedAnswer> result =
        ProblemMapper.buildAcceptedAnswers(
            selections, List.of(List.of("meiosis"), List.of("mitosis")));

    assertThat(result).extracting(AcceptedAnswer::answer).containsExactly("감수분열", "체세포분열");
    assertThat(result.get(0).accepted()).containsExactly("meiosis");
    assertThat(result.get(1).accepted()).containsExactly("mitosis");
  }

  @Test
  @DisplayName("허용변형이 null이면 answer만 채우고 accepted는 빈 목록")
  void null_variants_yields_empty_accepted() {
    List<Selection> selections = List.of(correct("투사"));
    List<AcceptedAnswer> result = ProblemMapper.buildAcceptedAnswers(selections, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).answer()).isEqualTo("투사");
    assertThat(result.get(0).accepted()).isEmpty();
  }

  @Test
  @DisplayName("허용변형 길이가 빈칸 수보다 짧으면 부족한 빈칸은 빈 accepted")
  void fewer_variants_than_blanks() {
    List<Selection> selections = List.of(correct("A, B, C"));
    List<AcceptedAnswer> result =
        ProblemMapper.buildAcceptedAnswers(selections, List.of(List.of("a1"), Arrays.asList("b1")));

    assertThat(result).extracting(AcceptedAnswer::answer).containsExactly("A", "B", "C");
    assertThat(result.get(0).accepted()).containsExactly("a1");
    assertThat(result.get(1).accepted()).containsExactly("b1");
    assertThat(result.get(2).accepted()).isEmpty();
  }

  @Test
  @DisplayName("정답 선지가 없으면 null(폴백 채점 대상)")
  void no_correct_selection_returns_null() {
    List<Selection> selections = List.of(wrong("합리화"), wrong("억압"));

    assertThat(ProblemMapper.buildAcceptedAnswers(selections, List.of(List.of("x")))).isNull();
  }
}
