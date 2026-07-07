package com.icc.qasker.quizset.service.quality;

import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.QualityReviewService;
import com.icc.qasker.quizset.dto.QualityReviewResult;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.QualityStatus;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.repository.ProblemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pass 2 재검토 서비스. problem(서빙)과 분리된 품질 로그(problem_quality_log)에서 rationale을 읽어 원문 없이 병렬 재검증(가상 스레드)한
 * 뒤, 미달 문항의 품질 로그 행만 순차 마킹한다. 통과·검증불가 행은 건드리지 않아 dirty tracking이 미달 subset만 UPDATE한다(SC-003). 검증기는
 * quiz-ai/api의 QualityVerifier 인터페이스에만 의존한다(헌법 III).
 */
@Slf4j
@Service
public class QualityReviewServiceImpl implements QualityReviewService {

  private final ProblemRepository problemRepository;
  private final ProblemQualityLogRepository qualityLogRepository;
  private final QualityVerifier qualityVerifier;
  private final TransactionTemplate transactionTemplate;
  private final Map<Long, QualityReviewResult> latestResults = new ConcurrentHashMap<>();

  public QualityReviewServiceImpl(
      ProblemRepository problemRepository,
      ProblemQualityLogRepository qualityLogRepository,
      QualityVerifier qualityVerifier,
      TransactionTemplate transactionTemplate) {
    this.problemRepository = problemRepository;
    this.qualityLogRepository = qualityLogRepository;
    this.qualityVerifier = qualityVerifier;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public QualityReviewResult review(Long problemSetId) {
    QualityReviewResult result = transactionTemplate.execute(status -> doReview(problemSetId));
    latestResults.put(problemSetId, result);
    return result;
  }

  @Override
  public List<QualityReviewResult> reviewBulk(List<Long> problemSetIds) {
    List<QualityReviewResult> results = new ArrayList<>();
    for (Long setId : problemSetIds) {
      try {
        results.add(review(setId));
      } catch (CustomException e) {
        // 레거시(rationale 없음) 세트는 일괄에서 건너뛴다(FR-014).
        log.info("[품질 재검토] 세트 {} 스킵: {}", setId, e.getMessage());
      }
    }
    return results;
  }

  @Async
  @Override
  public void submitReview(Long problemSetId) {
    try {
      review(problemSetId);
    } catch (CustomException e) {
      log.info("[품질 재검토] 비동기 세트 {} 스킵/실패: {}", problemSetId, e.getMessage());
    }
  }

  @Async
  @Override
  public void submitReviewBulk(List<Long> problemSetIds) {
    reviewBulk(problemSetIds);
  }

  @Override
  public Optional<QualityReviewResult> latestResult(Long problemSetId) {
    return Optional.ofNullable(latestResults.get(problemSetId));
  }

  private QualityReviewResult doReview(Long problemSetId) {
    List<Problem> problems = problemRepository.findByIdProblemSetIdIn(List.of(problemSetId));
    if (problems.isEmpty()) {
      throw new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND);
    }
    Map<Integer, Problem> problemByNumber =
        problems.stream()
            .collect(Collectors.toMap(p -> p.getId().getNumber(), Function.identity()));
    ProblemSet set = problems.get(0).getProblemSet();

    // rationale 보유 품질 로그 행만 대상. 레거시(품질 로그 없음/근거 없음)는 제외.
    List<ProblemQualityLog> qualities =
        qualityLogRepository.findByProblemSetIdIn(List.of(problemSetId));
    List<ProblemQualityLog> targets =
        qualities.stream()
            .filter(q -> q.getRationale() != null && problemByNumber.containsKey(q.getNumber()))
            .toList();
    int skipped = problems.size() - targets.size();
    if (targets.isEmpty()) {
      throw new CustomException(ExceptionMessage.QUALITY_REVIEW_NO_RATIONALE);
    }

    // 검증 요청은 트랜잭션 스레드에서 구성(엔티티 동시 접근 방지) 후, 병렬 검증엔 불변 DTO만 넘긴다.
    List<ReviewTask> tasks =
        targets.stream()
            .map(q -> new ReviewTask(q, toRequest(q, problemByNumber.get(q.getNumber()), set)))
            .toList();
    List<Verdicted> verdicts = verifyInParallel(tasks);

    int below = 0;
    for (Verdicted v : verdicts) {
      if (v.verdict().result() == QualityVerdict.Result.BELOW_THRESHOLD) {
        // 미달 subset만 마킹 → dirty. 통과·검증불가는 무변경(flush 스킵).
        v.quality().applyVerdict(QualityStatus.BELOW_THRESHOLD, v.verdict().feedback());
        below++;
      }
    }
    return new QualityReviewResult(problemSetId, targets.size(), below, skipped, "COMPLETED");
  }

  /** rationale 기반 재검증을 가상 스레드로 병렬 실행한다(DB 접근·엔티티 상태 변경 없음). */
  private List<Verdicted> verifyInParallel(List<ReviewTask> tasks) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Verdicted>> futures =
          tasks.stream()
              .map(
                  t ->
                      executor.submit(
                          () -> new Verdicted(t.quality(), qualityVerifier.verify(t.request()))))
              .toList();
      List<Verdicted> out = new ArrayList<>(futures.size());
      for (Future<Verdicted> f : futures) {
        try {
          out.add(f.get());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
        } catch (ExecutionException e) {
          throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
        }
      }
      return out;
    }
  }

  private QualityVerificationRequest toRequest(
      ProblemQualityLog quality, Problem problem, ProblemSet set) {
    String quizType = set.getQuizType() == null ? "MULTIPLE" : set.getQuizType().toAiStrategyName();
    List<QualityVerificationRequest.Selection> selections =
        problem.getSelections().stream()
            .map(s -> new QualityVerificationRequest.Selection(s.content(), s.correct()))
            .toList();
    String modelAnswer = "ESSAY".equals(quizType) ? firstCorrect(problem.getSelections()) : null;
    return new QualityVerificationRequest(
        quizType,
        "KO",
        problem.getTitle(),
        selections,
        modelAnswer,
        RationaleToAiMapper.toDto(quality.getRationale()),
        set.getCustomInstruction(),
        problem.getAppliedInstruction(),
        Mode.PASS_2);
  }

  private static String firstCorrect(List<Selection> selections) {
    return selections.stream()
        .filter(Selection::correct)
        .map(Selection::content)
        .findFirst()
        .orElse(null);
  }

  private record ReviewTask(ProblemQualityLog quality, QualityVerificationRequest request) {}

  private record Verdicted(ProblemQualityLog quality, QualityVerdict verdict) {}
}
