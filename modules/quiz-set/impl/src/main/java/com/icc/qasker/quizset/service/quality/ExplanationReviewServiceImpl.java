package com.icc.qasker.quizset.service.quality;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ExplanationReviewService;
import com.icc.qasker.quizset.dto.ExplanationReviewResult;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 해설 형식 검증(정규식) 온디맨드 구현. 로그 행의 해설(개선본 v2 우선, 없으면 첫 생성본 v1)을 {@link ExplanationFormatValidator}로
 * 검증하고, 필수 규칙 미달 문항의 review에만 위반 요약을 마킹한다(dirty subset). 통과분은 무변경, 서빙 problem은 건드리지 않는다.
 */
@Service
public class ExplanationReviewServiceImpl implements ExplanationReviewService {

  private final ProblemQualityLogRepository qualityLogRepository;
  private final ExplanationFormatValidator validator;
  private final TransactionTemplate transactionTemplate;

  public ExplanationReviewServiceImpl(
      ProblemQualityLogRepository qualityLogRepository,
      ExplanationFormatValidator validator,
      TransactionTemplate transactionTemplate) {
    this.qualityLogRepository = qualityLogRepository;
    this.validator = validator;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public ExplanationReviewResult review(Long problemSetId) {
    return transactionTemplate.execute(status -> doReview(problemSetId));
  }

  private ExplanationReviewResult doReview(Long problemSetId) {
    List<ProblemQualityLog> logs = qualityLogRepository.findByProblemSetIdIn(List.of(problemSetId));
    if (logs.isEmpty()) {
      throw new CustomException(ExceptionMessage.QUALITY_REVIEW_NO_TARGET);
    }

    int violations = 0;
    for (ProblemQualityLog log : logs) {
      String explanation =
          log.getV2Explanation() != null ? log.getV2Explanation() : log.getV1Explanation();
      ExplanationFormatValidator.Result result = validator.validate(explanation);

      if (!result.passed()) {
        // 형식 미달 문항만 review 마킹 → dirty subset. 통과분은 무변경(flush 스킵).
        log.markExplanationReview(result.summary());
        violations++;
      }
    }
    return new ExplanationReviewResult(problemSetId, logs.size(), violations);
  }
}
