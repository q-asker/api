package com.icc.qasker.quizset.service.grade;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.GradeRequest;
import com.icc.qasker.quizset.dto.ferequest.GradeRequest.GradeAnswer;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.feresponse.GradeResponse;
import com.icc.qasker.quizset.dto.feresponse.GradeResponse.GradeResult;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.grading.RealBlankGrader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * REAL_BLANK 무상태 채점 서비스(SSOT). {@link RealBlankGrader}로 결정적으로 판정한다. 저장하지 않으며 로그인 여부와 무관하게 동작한다.
 * REAL_BLANK 세트만 처리해 타 유형 채점을 건드리지 않는다(FR-007).
 */
@Service
@RequiredArgsConstructor
public class GradeService {

  private final ProblemSetReadService problemSetReadService;
  private final HashUtil hashUtil;

  public GradeResponse grade(GradeRequest request) {
    Long problemSetId = hashUtil.decode(request.problemSetId());
    ProblemSetSummary summary =
        problemSetReadService
            .findProblemSetById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    if (summary.quizType() != QuizType.REAL_BLANK) {
      throw new CustomException(ExceptionMessage.GRADE_UNSUPPORTED_QUIZ_TYPE);
    }

    Map<Integer, String> inputByNumber =
        request.answers().stream()
            .collect(
                Collectors.toMap(
                    GradeAnswer::number, a -> a.textAnswer() == null ? "" : a.textAnswer()));

    List<GradeResult> results =
        problemSetReadService.findProblemsByProblemSetId(problemSetId).stream()
            .map(problem -> gradeProblem(problem, inputByNumber.get(problem.number())))
            .toList();
    return new GradeResponse(results);
  }

  private GradeResult gradeProblem(ProblemDetail problem, String textAnswer) {
    RealBlankGrader.GradeOutcome outcome = RealBlankGrader.grade(problem.selections(), textAnswer);
    return new GradeResult(
        problem.number(), outcome.isCorrect(), outcome.answer(), outcome.acceptedAnswers());
  }
}
