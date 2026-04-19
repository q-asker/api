package com.icc.qasker.quizset.service.query;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ExplanationService;
import com.icc.qasker.quizset.dto.feresponse.ExplanationResponse;
import com.icc.qasker.quizset.dto.feresponse.ResultResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
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
  @Transactional(readOnly = true)
  public ExplanationResponse getExplanationByProblemSetId(String problemSetId) {
    long id = hashUtil.decode(problemSetId);

    List<Problem> problems = problemRepository.findByIdProblemSetId(id);
    String fileUrl =
        problemSetRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND))
            .getFileUrl();
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

    return new ExplanationResponse(results, fileUrl);
  }
}
