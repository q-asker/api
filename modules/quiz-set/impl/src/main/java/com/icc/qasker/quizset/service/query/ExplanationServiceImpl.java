package com.icc.qasker.quizset.service.query;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ExplanationService;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.dto.feresponse.ExplanationResponse;
import com.icc.qasker.quizset.dto.feresponse.ResultResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
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
    ProblemSet problemSet =
        problemSetRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    if (problems.isEmpty()) {
      throw new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND);
    }

    GenerationStatus status = problemSet.getGenerationStatus();
    List<ResultResponse> results =
        problems.stream().map(problem -> toResultResponse(problem, status)).toList();

    return new ExplanationResponse(results, problemSet.getFileUrl(), status);
  }

  /**
   * FE 하위호환: 현행 FE는 explanation null 분기가 없어(키 존재 기준) 치환 문자열을 유지한다. 치환 문구는 세트 상태에 따라 다르다 — 생성이
   * 종결(COMPLETED·FAILED)됐는데도 해설이 없으면 영구 부재이므로 "해설 없음", 아직 진행 중(GENERATING·PROBLEMS_READY)이면 곧 생성되므로
   * "해설 준비 중" 안내.
   */
  private ResultResponse toResultResponse(Problem problem, GenerationStatus status) {
    String explanation = problem.getExplanationContent();
    if (explanation == null) {
      explanation =
          (status == GenerationStatus.COMPLETED || status == GenerationStatus.FAILED)
              ? "해설 없음"
              : "해설 준비 중입니다. 잠시 후 기다려 주세요.";
    }
    return new ResultResponse(
        problem.getId().getNumber(), explanation, problem.getReferencedPages());
  }
}
