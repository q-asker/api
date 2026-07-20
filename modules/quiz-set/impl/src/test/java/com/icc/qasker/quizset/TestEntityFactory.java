package com.icc.qasker.quizset;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.mapper.ProblemSetResponseMapper;
import java.lang.reflect.Constructor;
import java.util.List;

/** 테스트 전용 엔티티/매퍼 생성 헬퍼. */
public final class TestEntityFactory {

  private TestEntityFactory() {}

  public static Problem problem(
      long problemSetId,
      int number,
      String title,
      List<Selection> selections,
      String explanation,
      String appliedInstruction,
      List<Integer> referencedPages) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().problemSetId(problemSetId).number(number).build())
            .title(title)
            .build();
    problem.bindQuizData(selections, referencedPages);
    problem.updateExplanation(explanation);
    problem.updateAppliedInstruction(appliedInstruction);
    return problem;
  }

  public static ProblemSet problemSet(
      long id,
      String sessionId,
      String title,
      GenerationStatus status,
      QuizType quizType,
      int totalQuizCount,
      String userId,
      List<Problem> problems) {
    return ProblemSet.builder()
        .id(id)
        .sessionId(sessionId)
        .title(title)
        .generationStatus(status)
        .quizType(quizType)
        .totalQuizCount(totalQuizCount)
        .userId(userId)
        .fileUrl("file-url")
        .problems(problems)
        .build();
  }

  /** private 생성자를 가진 매퍼를 리플렉션으로 생성한다. */
  public static ProblemSetResponseMapper responseMapper(HashUtil hashUtil) {
    try {
      Constructor<ProblemSetResponseMapper> constructor =
          ProblemSetResponseMapper.class.getDeclaredConstructor(HashUtil.class);
      constructor.setAccessible(true);
      return constructor.newInstance(hashUtil);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("테스트용 ProblemSetResponseMapper 생성 실패", e);
    }
  }
}
