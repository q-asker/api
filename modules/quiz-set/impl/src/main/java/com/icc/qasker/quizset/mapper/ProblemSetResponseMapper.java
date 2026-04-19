package com.icc.qasker.quizset.mapper;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe.SelectionForFE;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProblemSetResponseMapper {

  private final HashUtil hashUtil;

  public QuizForFe fromEntity(Problem problem) {
    List<SelectionForFE> selections =
        IntStream.range(0, problem.getSelections().size())
            .mapToObj(
                i -> {
                  Selection sel = problem.getSelections().get(i);
                  return new SelectionForFE(i + 1, sel.content(), sel.correct());
                })
            .toList();

    return new QuizForFe(problem.getId().getNumber(), problem.getTitle(), 0, false, selections);
  }

  public ProblemSetResponse fromEntity(ProblemSet problemSet) {
    List<QuizForFe> quizzes = problemSet.getProblems().stream().map(this::fromEntity).toList();

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
