package com.icc.qasker.quizset.service.quality;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ExplanationReviewService;
import com.icc.qasker.quizset.dto.ExplanationReviewResult;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 해설 형식 검증(정규식) 온디맨드 구현. 로그 행의 해설(개선본 v2 우선, 없으면 첫 생성본 v1)을 {@link ExplanationFormatValidator}로
 * 검증하고, 필수 규칙 미달 문항의 review에만 위반 요약을 마킹한다(dirty subset). 통과분은 무변경, 서빙 problem은 건드리지 않는다.
 *
 * <p>요청한 세트 전부를 한 SELECT로 읽고 한 트랜잭션에서 처리한다 — 세트 수만큼 왕복하던 SELECT·COMMIT·autocommit 토글이 1회로 준다. 대신 한
 * 세트라도 대상이 없으면 요청 전체가 롤백된다(부분 커밋 없음).
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
  public List<ExplanationReviewResult> review(List<Long> problemSetIds) {
    return transactionTemplate.execute(status -> doReview(problemSetIds));
  }

  private List<ExplanationReviewResult> doReview(List<Long> problemSetIds) {
    Map<Long, List<ProblemQualityLog>> logsBySet =
        qualityLogRepository.findByProblemSetIdIn(problemSetIds).stream()
            .collect(Collectors.groupingBy(ProblemQualityLog::getProblemSetId));

    List<ExplanationReviewResult> results = new ArrayList<>(problemSetIds.size());
    for (Long problemSetId : problemSetIds) {
      List<ProblemQualityLog> logs = logsBySet.get(problemSetId);
      if (logs == null) {
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
      results.add(new ExplanationReviewResult(problemSetId, logs.size(), violations));
    }
    return results;
  }
}
