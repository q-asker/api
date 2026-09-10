package com.icc.qasker.quizhistory.service.wronganswer;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizhistory.grading.AnswerJudge;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 풀이 기록에서 틀린 문항을 골라 유형별로 나눈다. 부수효과가 없어 규칙 자체를 단위 테스트로 고정할 수 있다.
 *
 * <p>순서가 규칙의 일부다 — <b>최근에 푼 순으로 정렬한 뒤</b> 중복을 걷어내고 마지막에 상한으로 자른다. 정렬보다 중복 제거가 앞서면 같은 문항의 오래된 쪽이 남아
 * 상한에서 최신 오답이 밀려난다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WrongAnswerCollector {

  /** 문제집 하나에 담기는 문항 수 상한. 유형마다 따로 적용되므로 한 번의 실행이 만드는 총 문항 수는 이보다 많을 수 있다. */
  public static final int MAX_QUESTIONS_PER_SET = 100;

  /**
   * 수집 대상이 된 기록 하나.
   *
   * @param solvedAt 풀이를 마친 시각. 최근에 틀린 순을 정하는 기준이다.
   */
  public record AnsweredSet(
      Long problemSetId,
      QuizType quizType,
      Instant solvedAt,
      List<ProblemDetail> problems,
      AnswerSnapshotView answers) {}

  /**
   * 한 유형에서 만들어질 문제집.
   *
   * @param sources 복제할 원본 문항의 좌표. 이 순서가 새 문제집의 문항 순서가 된다.
   * @param truncated 상한에 걸려 일부만 담겼는지.
   */
  public record TypeGroup(QuizType quizType, List<ProblemLineage> sources, boolean truncated) {}

  /** 유형별 문제집 구성을 만든다. 틀린 문항이 없는 유형은 결과에 들어가지 않는다. */
  public static List<TypeGroup> collect(List<AnsweredSet> answeredSets) {
    List<Candidate> candidates = new ArrayList<>();
    for (AnsweredSet set : answeredSets) {
      for (ProblemDetail problem : set.problems()) {
        if (AnswerJudge.isCorrect(set.quizType(), problem, set.answers())) {
          continue;
        }
        candidates.add(
            new Candidate(
                set.quizType(),
                new ProblemLineage(set.problemSetId(), problem.number()),
                problem.lineage(set.problemSetId()),
                set.solvedAt(),
                problem.number()));
      }
    }

    candidates.sort(
        Comparator.comparing(Candidate::solvedAt, Comparator.reverseOrder())
            .thenComparingInt(Candidate::number));

    Map<QuizType, List<Candidate>> byType = new LinkedHashMap<>();
    Set<ProblemLineage> seen = new HashSet<>();
    for (Candidate candidate : candidates) {
      // 같은 문항이 여러 문제집에 걸쳐 있어도(세대가 반복돼도) 한 번만 담는다. 정렬이 끝난 뒤라 먼저 만난 쪽이 더 최근이다.
      if (!seen.add(candidate.lineage())) {
        continue;
      }
      byType.computeIfAbsent(candidate.quizType(), type -> new ArrayList<>()).add(candidate);
    }

    return byType.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> toGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static TypeGroup toGroup(QuizType quizType, List<Candidate> candidates) {
    boolean truncated = candidates.size() > MAX_QUESTIONS_PER_SET;
    List<ProblemLineage> sources =
        candidates.stream().limit(MAX_QUESTIONS_PER_SET).map(Candidate::source).toList();
    return new TypeGroup(quizType, sources, truncated);
  }

  private record Candidate(
      QuizType quizType,
      ProblemLineage source,
      ProblemLineage lineage,
      Instant solvedAt,
      int number) {}
}
