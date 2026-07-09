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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Pass 2 재검토 서비스. problem(서빙)과 분리된 품질 로그(problem_quality_log) 행별로 문항을 원문 없이 병렬 재검증(가상 스레드)한 뒤, 미달
 * 문항의 품질 로그 행에만 재검토 결과(review·v2Feedback)를 순차 마킹한다. 통과·검증불가 행은 건드리지 않아 dirty tracking이 미달 subset만
 * UPDATE한다(SC-003). 검증기는 quiz-ai/api의 QualityVerifier 인터페이스에만 의존한다(헌법 III).
 */
@Slf4j
@Service
public class QualityReviewServiceImpl implements QualityReviewService {

  private final ProblemRepository problemRepository;
  private final ProblemQualityLogRepository qualityLogRepository;
  private final QualityVerifier qualityVerifier;
  private final TransactionTemplate transactionTemplate;
  private final Map<Long, QualityReviewResult> latestResults = new ConcurrentHashMap<>();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        // 품질 로그가 없어 재검토 대상이 없는 세트는 일괄에서 건너뛴다.
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

    // 품질 로그 행이 있는 문항만 대상(문항 매칭). 품질 로그가 없는 세트는 재검토할 대상이 없다.
    List<ProblemQualityLog> qualities =
        qualityLogRepository.findByProblemSetIdIn(List.of(problemSetId));
    List<ProblemQualityLog> targets =
        qualities.stream().filter(q -> problemByNumber.containsKey(q.getNumber())).toList();
    int skipped = problems.size() - targets.size();
    if (targets.isEmpty()) {
      throw new CustomException(ExceptionMessage.QUALITY_REVIEW_NO_TARGET);
    }

    // 검증 요청은 트랜잭션 스레드에서 구성(엔티티 동시 접근 방지) 후, 병렬 검증엔 불변 DTO만 넘긴다.
    List<ReviewTask> tasks =
        targets.stream().map(q -> new ReviewTask(q, toRequest(q, set))).toList();
    List<Verdicted> verdicts = verifyInParallel(tasks);

    int below = 0;
    for (Verdicted v : verdicts) {
      if (v.verdict().result() == QualityVerdict.Result.BELOW_THRESHOLD) {
        // 미달 문항만 마킹 → dirty. 통과·검증불가는 무변경(flush 스킵).
        v.quality().markQuestionVerdict(v.verdict().feedback());
        below++;
      }
    }
    return new QualityReviewResult(problemSetId, targets.size(), below, skipped, "COMPLETED");
  }

  /** 문항 재검증을 가상 스레드로 병렬 실행한다(DB 접근·엔티티 상태 변경 없음). */
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

  /** 재검토 요청을 서빙 problem이 아니라 로그 행에서 재구성한다(로그 자기완결). 개선본(v2) 우선, 없으면 첫 생성본(v1). */
  private QualityVerificationRequest toRequest(ProblemQualityLog quality, ProblemSet set) {
    String quizType = set.getQuizType() == null ? "MULTIPLE" : set.getQuizType().toAiStrategyName();
    QuestionSnapshot snapshot = deserializeQuestion(quality);
    List<QualityVerificationRequest.Selection> selections =
        snapshot.selections().stream()
            .map(s -> new QualityVerificationRequest.Selection(s.content(), s.correct()))
            .toList();
    String modelAnswer = "ESSAY".equals(quizType) ? firstCorrect(snapshot.selections()) : null;
    return new QualityVerificationRequest(
        quizType,
        "KO",
        snapshot.stem(),
        selections,
        modelAnswer,
        set.getCustomInstruction(),
        snapshot.appliedInstruction(),
        Mode.PASS_2,
        // 재생성본이면 round 1 미달 사유(v1Feedback)를 함께 넘겨 반영 여부까지 판정하게 한다(통과분은 null).
        quality.getV1Feedback(),
        null);
  }

  /** 로그 행의 개선본(v2) 질문 우선, 없으면 첫 생성본(v1) 질문 JSON을 역직렬화한다. */
  @SuppressWarnings("unchecked")
  private static QuestionSnapshot deserializeQuestion(ProblemQualityLog quality) {
    String json =
        quality.getV2QuestionJson() != null
            ? quality.getV2QuestionJson()
            : quality.getV1QuestionJson();
    if (json == null || json.isBlank()) {
      return new QuestionSnapshot("", List.of(), null);
    }
    try {
      Map<String, Object> map = OBJECT_MAPPER.readValue(json, Map.class);
      String stem = map.get("stem") instanceof String s ? s : "";
      String appliedInstruction = map.get("appliedInstruction") instanceof String a ? a : null;
      List<SelectionSnapshot> selections = new ArrayList<>();
      if (map.get("selections") instanceof List<?> rawSelections) {
        for (Object raw : rawSelections) {
          if (raw instanceof Map<?, ?> sel) {
            selections.add(
                new SelectionSnapshot(
                    sel.get("content") instanceof String c ? c : "",
                    Boolean.TRUE.equals(sel.get("correct"))));
          }
        }
      }
      return new QuestionSnapshot(stem, selections, appliedInstruction);
    } catch (JacksonException e) {
      throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
    }
  }

  private static String firstCorrect(List<SelectionSnapshot> selections) {
    return selections.stream()
        .filter(SelectionSnapshot::correct)
        .map(SelectionSnapshot::content)
        .findFirst()
        .orElse(null);
  }

  private record QuestionSnapshot(
      String stem, List<SelectionSnapshot> selections, String appliedInstruction) {}

  private record SelectionSnapshot(String content, boolean correct) {}

  private record ReviewTask(ProblemQualityLog quality, QualityVerificationRequest request) {}

  private record Verdicted(ProblemQualityLog quality, QualityVerdict verdict) {}
}
