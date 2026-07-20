package com.icc.qasker.quizset.service.quality;

import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.QualityReviewService;
import com.icc.qasker.quizset.dto.QualityReviewResult;
import com.icc.qasker.quizset.entity.ProblemQualityLog;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
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
 * Pass 2 재검토 서비스. 주어진 세트 id들의 품질 로그(problem_quality_log)만으로 문항을 원문 없이 병렬 재검증(가상 스레드)한 뒤, 미달 문항의 품질
 * 로그 행에만 재검토 결과(v2Feedback)를 마킹한다. 통과·검증불가 행은 건드리지 않아 dirty tracking이 미달 subset만 UPDATE한다(SC-003).
 *
 * <p>외부 API(Gemini) 호출은 트랜잭션 밖에서 수행한다: TX1(로드+검증요청 구성) → 병렬 검증 → TX2(미달분 재조회·마킹). 엔티티를 트랜잭션 경계 밖으로
 * 내보내지 않고 logId·setId만 넘긴다(준영속 접근·flush 누락 방지). 검증기는 quiz-ai/api의 QualityVerifier 인터페이스에만 의존한다(헌법
 * III).
 */
@Slf4j
@Service
public class QualityReviewServiceImpl implements QualityReviewService {

  private final ProblemSetRepository problemSetRepository;
  private final ProblemQualityLogRepository qualityLogRepository;
  private final QualityVerifier qualityVerifier;
  private final TransactionTemplate transactionTemplate;
  private final Map<Long, QualityReviewResult> latestResults = new ConcurrentHashMap<>();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public QualityReviewServiceImpl(
      ProblemSetRepository problemSetRepository,
      ProblemQualityLogRepository qualityLogRepository,
      QualityVerifier qualityVerifier,
      TransactionTemplate transactionTemplate) {
    this.problemSetRepository = problemSetRepository;
    this.qualityLogRepository = qualityLogRepository;
    this.qualityVerifier = qualityVerifier;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void review(List<Long> problemSetIds) {
    // TX1: 품질 로그 로드 + 검증요청 DTO 구성 (엔티티는 밖으로 내보내지 않음)
    List<ReviewTask> tasks =
        transactionTemplate.execute(status -> prepareReviewTasks(problemSetIds));

    // 트랜잭션 밖: 외부 API(Gemini) 병렬 검증 — 커넥션 미보유
    List<Verdicted> verdicts = verifyInParallel(tasks);

    // TX2: 미달분 재조회 후 마킹 + 세트별 결과 집계
    transactionTemplate.executeWithoutResult(status -> applyVerdicts(verdicts));
  }

  @Async
  @Override
  public void submitReview(Long problemSetId) {
    try {
      review(List.of(problemSetId));
    } catch (CustomException e) {
      log.info("[품질 재검토] 비동기 세트 {} 스킵/실패: {}", problemSetId, e.getMessage());
    }
  }

  @Async
  @Override
  public void submitReviewBulk(List<Long> problemSetIds) {
    try {
      review(problemSetIds);
    } catch (CustomException e) {
      log.info("[품질 재검토] 비동기 일괄 {} 스킵/실패: {}", problemSetIds, e.getMessage());
    }
  }

  @Override
  public Optional<QualityReviewResult> latestResult(Long problemSetId) {
    return Optional.ofNullable(latestResults.get(problemSetId));
  }

  /** TX1: 세트 id들의 품질 로그(pass2 eager) + 세트 정보를 로드해 불변 검증요청 태스크를 만든다. */
  private List<ReviewTask> prepareReviewTasks(List<Long> problemSetIds) {
    List<ProblemQualityLog> targets =
        qualityLogRepository.findWithPass2ByProblemSetIdIn(problemSetIds);
    if (targets.isEmpty()) {
      throw new CustomException(ExceptionMessage.QUALITY_REVIEW_NO_TARGET);
    }
    Map<Long, ProblemSet> setById =
        problemSetRepository.findAllById(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSet::getId, Function.identity()));
    return targets.stream()
        .filter(q -> setById.containsKey(q.getProblemSetId()))
        .map(
            q ->
                new ReviewTask(
                    q.getId(), q.getProblemSetId(), toRequest(q, setById.get(q.getProblemSetId()))))
        .toList();
  }

  /** 문항 재검증을 가상 스레드로 병렬 실행한다(DB 접근·엔티티 상태 변경 없음, 불변 DTO만 사용). */
  private List<Verdicted> verifyInParallel(List<ReviewTask> tasks) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Verdicted>> futures =
          tasks.stream()
              .map(
                  t ->
                      executor.submit(
                          () ->
                              new Verdicted(
                                  t.logId(), t.setId(), qualityVerifier.verify(t.request()))))
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

  /** TX2: 미달분 로그만 재조회(managed)해 v2Feedback 마킹(@DynamicUpdate로 해당 컬럼만 UPDATE) + 세트별 결과 집계. */
  private void applyVerdicts(List<Verdicted> verdicts) {
    List<Long> belowLogIds =
        verdicts.stream()
            .filter(v -> v.verdict().result() == QualityVerdict.Result.BELOW_THRESHOLD)
            .map(Verdicted::logId)
            .toList();
    Map<Long, ProblemQualityLog> belowById =
        qualityLogRepository.findAllById(belowLogIds).stream()
            .collect(Collectors.toMap(ProblemQualityLog::getId, Function.identity()));
    verdicts.stream()
        .filter(v -> v.verdict().result() == QualityVerdict.Result.BELOW_THRESHOLD)
        .forEach(v -> belowById.get(v.logId()).markQuestionVerdict(v.verdict().feedback()));

    Map<Long, List<Verdicted>> bySet =
        verdicts.stream().collect(Collectors.groupingBy(Verdicted::setId));
    bySet.forEach(
        (setId, list) -> {
          int reviewed = list.size();
          int below =
              (int)
                  list.stream()
                      .filter(v -> v.verdict().result() == QualityVerdict.Result.BELOW_THRESHOLD)
                      .count();
          latestResults.put(setId, new QualityReviewResult(setId, reviewed, below, "COMPLETED"));
        });
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

  private record ReviewTask(long logId, long setId, QualityVerificationRequest request) {}

  private record Verdicted(long logId, long setId, QualityVerdict verdict) {}
}
