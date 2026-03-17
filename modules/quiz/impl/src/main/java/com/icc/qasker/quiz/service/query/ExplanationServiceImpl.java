package com.icc.qasker.quiz.service.query;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.ExplanationService;
import com.icc.qasker.quiz.ExplanationStatus;
import com.icc.qasker.quiz.dto.feresponse.ExplanationResponse;
import com.icc.qasker.quiz.dto.feresponse.ResultResponse;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplanationServiceImpl implements ExplanationService {

  private final HashUtil hashUtil;
  private final ProblemRepository problemRepository;
  private final ProblemSetRepository problemSetRepository;

  @Override
  public ExplanationResponse getExplanationByProblemSetId(String problemSetId) {
    long id = hashUtil.decode(problemSetId);

    ProblemSet problemSet =
        problemSetRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    ExplanationStatus status = problemSet.getExplanationStatus();

    List<Problem> problems = problemRepository.findByIdProblemSetId(id);
    if (problems.isEmpty()) {
      throw new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND);
    }

    List<ResultResponse> results =
        problems.stream()
            .map(
                problem -> {
                  String explanation =
                      problem.getExplanationContent() != null
                          ? problem.getExplanationContent()
                          : "해설 없음";
                  return new ResultResponse(
                      problem.getId().getNumber(), explanation, problem.getReferencedPages());
                })
            .toList();

    return new ExplanationResponse(status, results);
  }
}
