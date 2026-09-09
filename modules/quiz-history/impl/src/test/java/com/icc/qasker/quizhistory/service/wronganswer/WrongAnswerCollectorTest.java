package com.icc.qasker.quizhistory.service.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizhistory.service.wronganswer.WrongAnswerCollector.AnsweredSet;
import com.icc.qasker.quizhistory.service.wronganswer.WrongAnswerCollector.TypeGroup;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 오답 수집 규칙을 고정한다. 문제집당 상한·"가장 최근에 틀린 순" 우선순위·중복 제거는 이 테스트가 유일한 검증처다(기능 E2E는 100문항 시드를 만들지 않는다). */
class WrongAnswerCollectorTest {

  private static final Instant OLD = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant RECENT = Instant.parse("2026-06-01T00:00:00Z");

  /** 2번 선택지가 정답인 4지선다. */
  private static ProblemDetail problem(int number) {
    return new ProblemDetail(
        number,
        "문항 " + number,
        List.of(
            new SelectionDetail("1번", false),
            new SelectionDetail("2번", true),
            new SelectionDetail("3번", false),
            new SelectionDetail("4번", false)),
        "해설");
  }

  /** 재출제된 문항 — 최초 조상을 가리킨다. */
  private static ProblemDetail clonedProblem(int number, long ancestorSetId, int ancestorNumber) {
    ProblemDetail origin = problem(number);
    return new ProblemDetail(
        number,
        origin.title(),
        origin.selections(),
        origin.explanationContent(),
        ancestorSetId,
        ancestorNumber);
  }

  private static AnswerSnapshotView answers(List<AnswerSnapshot> snapshots) {
    return AnswerSnapshotView.from(snapshots);
  }

  /** 지정한 번호만 틀리게 답한 스냅샷(정답은 2번). */
  private static AnswerSnapshotView wrongOn(List<Integer> wrongNumbers, List<Integer> allNumbers) {
    List<AnswerSnapshot> snapshots = new ArrayList<>();
    for (int number : allNumbers) {
      int given = wrongNumbers.contains(number) ? 3 : 2;
      snapshots.add(new AnswerSnapshot(number, given, false, null));
    }
    return answers(snapshots);
  }

  @Nested
  @DisplayName("무엇을 틀린 것으로 보는가")
  class Judging {

    @Test
    @DisplayName("맞힌 문항은 담기지 않는다")
    void skipsCorrect() {
      AnsweredSet set =
          new AnsweredSet(
              1L,
              QuizType.MULTIPLE,
              RECENT,
              List.of(problem(1), problem(2)),
              wrongOn(List.of(2), List.of(1, 2)));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(set));

      assertThat(groups).hasSize(1);
      assertThat(groups.getFirst().sources()).containsExactly(new ProblemLineage(1L, 2));
    }

    @Test
    @DisplayName("답을 내지 않은 문항은 오답으로 담긴다")
    void unansweredIsWrong() {
      AnsweredSet set =
          new AnsweredSet(
              1L,
              QuizType.MULTIPLE,
              RECENT,
              List.of(problem(1)),
              answers(List.of(new AnswerSnapshot(1, 0, false, null))));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(set));

      assertThat(groups.getFirst().sources()).containsExactly(new ProblemLineage(1L, 1));
    }

    @Test
    @DisplayName("빈칸 직접입력은 표기가 흔들려도 인정 표현에 들면 오답이 아니다")
    void realBlankUsesGrader() {
      ProblemDetail blank =
          new ProblemDetail(
              1,
              "빈칸",
              List.of(new SelectionDetail("정답", true, List.of(List.of("정답", "answer")))),
              "해설");
      AnsweredSet correct =
          new AnsweredSet(
              1L,
              QuizType.REAL_BLANK,
              RECENT,
              List.of(blank),
              answers(List.of(new AnswerSnapshot(1, 0, false, " 정답. "))));
      AnsweredSet wrong =
          new AnsweredSet(
              2L,
              QuizType.REAL_BLANK,
              RECENT,
              List.of(blank),
              answers(List.of(new AnswerSnapshot(1, 0, false, "엉뚱한 말"))));

      assertThat(WrongAnswerCollector.collect(List.of(correct))).isEmpty();
      assertThat(WrongAnswerCollector.collect(List.of(wrong))).hasSize(1);
    }
  }

  @Nested
  @DisplayName("유형별로 나눈다 (SC-004a)")
  class Splitting {

    @Test
    @DisplayName("유형이 섞이면 유형마다 하나씩 만들고, 한 문제집에는 한 유형만 담긴다")
    void splitsByType() {
      AnsweredSet multiple =
          new AnsweredSet(
              1L, QuizType.MULTIPLE, RECENT, List.of(problem(1)), wrongOn(List.of(1), List.of(1)));
      AnsweredSet ox =
          new AnsweredSet(
              2L, QuizType.OX, RECENT, List.of(problem(1)), wrongOn(List.of(1), List.of(1)));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(multiple, ox));

      assertThat(groups).hasSize(2);
      assertThat(groups)
          .extracting(TypeGroup::quizType)
          .containsExactly(QuizType.MULTIPLE, QuizType.OX);
      assertThat(groups).allSatisfy(group -> assertThat(group.sources()).hasSize(1));
    }

    @Test
    @DisplayName("틀린 문항이 없는 유형은 만들지 않는다")
    void skipsTypeWithoutWrongAnswers() {
      AnsweredSet allCorrect =
          new AnsweredSet(
              1L, QuizType.OX, RECENT, List.of(problem(1)), wrongOn(List.of(), List.of(1)));

      assertThat(WrongAnswerCollector.collect(List.of(allCorrect))).isEmpty();
    }
  }

  @Nested
  @DisplayName("중복을 걷어낸다 (FR-005)")
  class Deduplication {

    @Test
    @DisplayName("여러 문제집에 걸쳐 같은 조상을 가진 문항은 한 번만 담긴다")
    void dedupesByLineage() {
      AnsweredSet origin =
          new AnsweredSet(
              1L, QuizType.MULTIPLE, OLD, List.of(problem(7)), wrongOn(List.of(7), List.of(7)));
      // 1번 세트 7번 문항을 재출제한 오답 문제집. 다시 풀어 또 틀렸다.
      AnsweredSet regenerated =
          new AnsweredSet(
              2L,
              QuizType.MULTIPLE,
              RECENT,
              List.of(clonedProblem(1, 1L, 7)),
              wrongOn(List.of(1), List.of(1)));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(origin, regenerated));

      assertThat(groups.getFirst().sources()).hasSize(1);
    }

    @Test
    @DisplayName("중복 중 남는 것은 더 최근에 푼 쪽이다 — 정렬이 중복 제거보다 앞선다")
    void keepsMostRecentDuplicate() {
      AnsweredSet older =
          new AnsweredSet(
              1L, QuizType.MULTIPLE, OLD, List.of(problem(7)), wrongOn(List.of(7), List.of(7)));
      AnsweredSet newer =
          new AnsweredSet(
              2L,
              QuizType.MULTIPLE,
              RECENT,
              List.of(clonedProblem(1, 1L, 7)),
              wrongOn(List.of(1), List.of(1)));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(older, newer));

      // 오래된 쪽이 남으면 상한에서 최신 오답이 밀려난다. 최근에 푼 2번 세트의 문항이 남아야 한다.
      assertThat(groups.getFirst().sources()).containsExactly(new ProblemLineage(2L, 1));
    }
  }

  @Nested
  @DisplayName("상한을 지킨다 (FR-006 · SC-004)")
  class Capping {

    private AnsweredSet setOf(long problemSetId, Instant solvedAt, int count) {
      List<Integer> numbers = IntStream.rangeClosed(1, count).boxed().toList();
      List<ProblemDetail> problems =
          numbers.stream().map(WrongAnswerCollectorTest::problem).toList();
      return new AnsweredSet(
          problemSetId, QuizType.MULTIPLE, solvedAt, problems, wrongOn(numbers, numbers));
    }

    @Test
    @DisplayName("101문항이 모이면 100개만 담고 잘렸음을 알린다")
    void capsAtHundred() {
      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(setOf(1L, RECENT, 101)));

      TypeGroup group = groups.getFirst();
      assertThat(group.sources()).hasSize(WrongAnswerCollector.MAX_QUESTIONS_PER_SET);
      assertThat(group.truncated()).isTrue();
    }

    @Test
    @DisplayName("상한에 걸리지 않으면 잘렸다고 하지 않는다")
    void notTruncatedUnderCap() {
      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(setOf(1L, RECENT, 100)));

      assertThat(groups.getFirst().sources()).hasSize(100);
      assertThat(groups.getFirst().truncated()).isFalse();
    }

    @Test
    @DisplayName("잘릴 때 남는 것은 가장 최근에 틀린 문항이다")
    void keepsMostRecentWhenTruncated() {
      // 오래 전에 푼 100문항 + 최근에 푼 1문항 → 최근 것이 반드시 살아남아야 한다.
      List<TypeGroup> groups =
          WrongAnswerCollector.collect(List.of(setOf(1L, OLD, 100), setOf(2L, RECENT, 1)));

      TypeGroup group = groups.getFirst();
      assertThat(group.sources()).hasSize(100);
      assertThat(group.sources().getFirst()).isEqualTo(new ProblemLineage(2L, 1));
      assertThat(group.truncated()).isTrue();
    }

    @Test
    @DisplayName("푼 시각이 모두 같으면 문항 번호가 앞선 것부터 남는다")
    void breaksTieByQuestionNumber() {
      // 완료 시각 컬럼이 생기기 전 기록은 백필로 전부 같은 값이 될 수 있다. 그 구간에서 무엇이 잘려나갈지를
      // 정하는 것은 문항 번호뿐이므로, 상한과 맞물리는 이 순서를 고정해 둔다.
      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(setOf(1L, OLD, 101)));

      List<ProblemLineage> sources = groups.getFirst().sources();
      assertThat(sources.getFirst()).isEqualTo(new ProblemLineage(1L, 1));
      assertThat(sources.getLast()).isEqualTo(new ProblemLineage(1L, 100));
      // 잘려나간 것은 번호가 가장 뒤인 101번이다.
      assertThat(sources).doesNotContain(new ProblemLineage(1L, 101));
    }

    @Test
    @DisplayName("상한은 문제집 하나마다 적용된다 — 한 유형이 잘려도 다른 유형은 영향받지 않는다")
    void capIsPerType() {
      List<Integer> numbers = IntStream.rangeClosed(1, 101).boxed().toList();
      AnsweredSet multiple = setOf(1L, RECENT, 101);
      AnsweredSet ox =
          new AnsweredSet(
              2L, QuizType.OX, RECENT, List.of(problem(1)), wrongOn(List.of(1), List.of(1)));

      List<TypeGroup> groups = WrongAnswerCollector.collect(List.of(multiple, ox));

      assertThat(numbers).hasSize(101);
      assertThat(groups).hasSize(2);
      assertThat(groups.getFirst().truncated()).isTrue();
      assertThat(groups.getLast().sources()).hasSize(1);
      assertThat(groups.getLast().truncated()).isFalse();
    }
  }
}
