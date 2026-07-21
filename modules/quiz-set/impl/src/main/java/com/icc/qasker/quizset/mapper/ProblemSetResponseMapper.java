package com.icc.qasker.quizset.mapper;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe.SelectionForFE;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProblemSetResponseMapper {

  private final HashUtil hashUtil;

  public QuizForFe fromEntity(Problem problem) {
    // 풀이 응답 경량화: 문항 해설·선지별 해설은 FE 미사용(실증)이므로 값을 비운다.
    // getExplanationContent()를 호출하지 않아 lazy 컬럼 초기화(N+1)를 유발하지 않는다 — Q6 lazy의 성립 조건.
    List<SelectionForFE> selections =
        QuizMappingSupport.mapSelections(
            problem.getSelections(),
            (id, sel) -> new SelectionForFE(id, sel.content(), null, sel.correct()));

    return new QuizForFe(
        problem.getId().getNumber(),
        problem.getTitle(),
        QuizMappingSupport.UNANSWERED_USER_ANSWER,
        QuizMappingSupport.UNCHECKED,
        selections,
        null,
        problem.getAppliedInstruction());
  }

  public ProblemSetResponse fromEntity(ProblemSet problemSet) {
    return toResponse(problemSet, problemSet.getProblems());
  }

  /** 세트 메타데이터는 problemSet에서, 문항 목록은 전달받은 problems에서 조립한다. */
  public ProblemSetResponse toResponse(ProblemSet problemSet, List<Problem> problems) {
    List<QuizForFe> quizzes = problems.stream().map(this::fromEntity).toList();

    return new ProblemSetResponse(
        problemSet.getSessionId(),
        hashUtil.encode(problemSet.getId()),
        problemSet.getTitle(),
        problemSet.getGenerationStatus(),
        problemSet.getQuizType(),
        problemSet.getTotalQuizCount(),
        quizzes);
  }
}
