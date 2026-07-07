package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.GENERATING;
import static com.icc.qasker.quizset.GenerationStatus.PROBLEMS_READY;

import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIExplanation;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.QualityMark;
import com.icc.qasker.ai.dto.RegenerationRecord;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizmake.mapper.AIProblemSetMapper;
import com.icc.qasker.quizmake.mapper.ExplanationMarkdownBuilder;
import com.icc.qasker.quizset.QualityLogService;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.QualityLogEntry;
import com.icc.qasker.quizset.dto.airesponse.ExplanationGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.view.QuizView;
import com.icc.qasker.quizset.view.QuizViewToQuizForFeMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * 배치 인터리빙 생성(Q9)의 저장 콜백 구현. 오케스트레이터가 Phase 1(문제)·Phase 2(해설)를 배치 단위로 교차 호출한다.
 *
 * <ul>
 *   <li>{@link #saveProblem}: 문제 1건을 저장하고 SSE로 통지한다. 선지는 오케스트레이터가 이미 최종 순서로 정렬했으므로 재정렬하지 않는다. 선지에
 *       해설이 인라인으로 포함된 단일 호출 타입(ESSAY)은 이 시점에 해설 마크다운까지 조립·저장하고, 2단계 타입(MULTIPLE/BLANK/OX)은 해설을 비워
 *       둔다.
 *   <li>{@link #saveExplanations}: Phase 2 해설을 저장 순서에 맞춰 마크다운으로 조립하고 후속 저장한다.
 * </ul>
 *
 * <p>여러 배치·단계가 동시에 도착해도 순번과 상태가 어긋나지 않도록 {@link ReentrantLock}으로 직렬화한다.
 */
@Slf4j
class GenerationBatchConsumer implements QuizBatchSink {

  private final String sessionId;
  private final Long problemSetId;
  private final GenerationRequest request;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final SseNotificationService notificationService;
  private final HashUtil hashUtil;
  private final QualityLogService qualityLogService;
  private final int explanationBatchSize;

  private final AtomicInteger generatedCount = new AtomicInteger(0);
  private final AtomicInteger numberCounter = new AtomicInteger(1);
  private final AtomicLong firstConsumerNanos = new AtomicLong(0);
  private final AtomicLong lastConsumerNanos = new AtomicLong(0);
  private final ReentrantLock consumerLock = new ReentrantLock();

  // Phase 2 해설 조립에 필요한 저장 시점 컨텍스트 (번호 → bloomsLevel + 저장 순서 선지)
  private final Map<Integer, PendingProblem> pending = new ConcurrentHashMap<>();

  // Phase 2 해설 배치 버퍼: N건 모이면 단일 트랜잭션으로 일괄 저장(커밋·fsync·왕복 N→1). consumerLock으로 보호.
  private final List<ExplanationGeneratedFromAI> explanationBuffer = new ArrayList<>();

  GenerationBatchConsumer(
      String sessionId,
      Long problemSetId,
      GenerationRequest request,
      QuizCommandService quizCommandService,
      QuizQueryService quizQueryService,
      SseNotificationService notificationService,
      HashUtil hashUtil,
      QualityLogService qualityLogService,
      int explanationBatchSize) {
    this.sessionId = sessionId;
    this.problemSetId = problemSetId;
    this.request = request;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.notificationService = notificationService;
    this.hashUtil = hashUtil;
    this.qualityLogService = qualityLogService;
    this.explanationBatchSize = Math.max(1, explanationBatchSize);
  }

  @Override
  public int saveProblem(AIProblem problem) {
    consumerLock.lock();
    try {
      QuizGeneratedFromAI quiz = AIProblemSetMapper.toQuiz(problem);

      // 단일 호출 타입(ESSAY): 선지에 해설이 인라인으로 있으면 즉시 마크다운 조립. 2단계 타입: 해설은 Phase 2까지 비워 둔다.
      if (hasInlineExplanation(quiz)) {
        quiz.setExplanation(ExplanationMarkdownBuilder.build(quiz, request.language()));
      } else {
        quiz.setExplanation(null);
      }

      int number = numberCounter.getAndIncrement();
      quiz.setNumber(number);

      List<Integer> savedNumbers = quizCommandService.saveBatch(List.of(quiz), problemSetId);
      pending.put(number, new PendingProblem(quiz.getBloomsLevel(), quiz.getSelections()));

      sendCreated(savedNumbers);
      upsertQuality(problem, number); // 품질/생성 근거는 problem_quality_log로 분리 저장(서빙 problem과 무관)

      generatedCount.incrementAndGet();
      firstConsumerNanos.compareAndSet(0, System.nanoTime());
      lastConsumerNanos.set(System.nanoTime());
      return number;
    } finally {
      consumerLock.unlock();
    }
  }

  /** 게이트가 부여한 품질(생성 근거·판정)을 품질 로그에 upsert한다. 게이트를 거치지 않은 문항(근거·마크 모두 없음)은 건너뛴다. */
  private void upsertQuality(AIProblem problem, int number) {
    QualityMark mark = problem.qualityMark();
    if (problem.rationale() == null && mark == null) {
      return;
    }
    try {
      qualityLogService.upsertQuality(
          new QualityLogEntry(
              problemSetId,
              number,
              AIProblemSetMapper.toRationaleOfAI(problem.rationale()),
              mark == null ? null : mark.status(),
              mark == null ? null : mark.feedback()));
    } catch (Exception e) {
      log.warn("[품질 로그] upsert 실패 problemSetId={} number={}", problemSetId, number, e);
    }
  }

  @Override
  public void markProblemsReady() {
    quizCommandService.updateStatus(problemSetId, PROBLEMS_READY);
  }

  @Override
  public void logRegeneration(RegenerationRecord record) {
    // 재생성 원본(v1)을 품질 로그의 해당 문항 행에 부착. 분석 전용 best-effort.
    try {
      qualityLogService.attachRegenerationSource(
          problemSetId, record.number(), record.v1Json(), record.v1Feedback());
    } catch (Exception e) {
      log.warn("[재생성 로그] v1 부착 실패 problemSetId={} number={}", problemSetId, record.number(), e);
    }
  }

  @Override
  public void saveExplanation(AIExplanation explanation) {
    consumerLock.lock();
    try {
      PendingProblem p = pending.remove(explanation.number());
      if (p == null) {
        log.warn("[해설 후속 저장] 대기 컨텍스트 없음, 건너뜀 number={}", explanation.number());
        return;
      }
      String markdown = buildExplanationMarkdown(p, explanation.selectionExplanations());
      explanationBuffer.add(
          new ExplanationGeneratedFromAI(
              explanation.number(), markdown, explanation.selectionExplanations()));
      if (explanationBuffer.size() >= explanationBatchSize) {
        flushBuffer();
      }
    } finally {
      consumerLock.unlock();
    }
  }

  /** 버퍼에 남은 해설을 마저 저장한다(생성 종료 시 호출 — N 미만 잔여분 보장 저장). */
  void flushExplanations() {
    consumerLock.lock();
    try {
      flushBuffer();
    } finally {
      consumerLock.unlock();
    }
  }

  /** consumerLock 보유 상태에서 버퍼를 단일 트랜잭션으로 일괄 저장하고 비운다. */
  private void flushBuffer() {
    if (explanationBuffer.isEmpty()) {
      return;
    }
    quizCommandService.saveExplanations(problemSetId, new ArrayList<>(explanationBuffer));
    explanationBuffer.clear();
  }

  /** 저장 순서 선지에 Phase 2 선지별 해설을 병합해 문항 해설 마크다운을 조립한다. */
  private String buildExplanationMarkdown(PendingProblem p, List<String> selectionExplanations) {
    QuizGeneratedFromAI tmp = new QuizGeneratedFromAI();
    tmp.setBloomsLevel(p.bloomsLevel());
    List<SelectionsOfAI> merged = new ArrayList<>(p.selections().size());
    for (int i = 0; i < p.selections().size(); i++) {
      SelectionsOfAI src = p.selections().get(i);
      SelectionsOfAI copy = new SelectionsOfAI();
      copy.setContent(src.getContent());
      copy.setCorrect(src.isCorrect());
      if (selectionExplanations != null && i < selectionExplanations.size()) {
        copy.setExplanation(selectionExplanations.get(i));
      }
      merged.add(copy);
    }
    tmp.setSelections(merged);
    return ExplanationMarkdownBuilder.build(tmp, request.language());
  }

  private void sendCreated(List<Integer> savedNumbers) {
    List<QuizView> quizViews = quizQueryService.getQuizViews(problemSetId, savedNumbers);
    if (quizViews.isEmpty()) return;

    List<QuizForFe> quizForFeList =
        quizViews.stream().map(QuizViewToQuizForFeMapper::toQuizForFe).toList();

    notificationService.sendCreatedMessageWithId(
        sessionId,
        String.valueOf(quizViews.getLast().getNumber()),
        new ProblemSetResponse(
            sessionId,
            hashUtil.encode(problemSetId),
            request.title(),
            GENERATING,
            request.quizType(),
            request.quizCount(),
            quizForFeList));
  }

  private static boolean hasInlineExplanation(QuizGeneratedFromAI quiz) {
    if (CollectionUtils.isEmpty(quiz.getSelections())) return false;
    return quiz.getSelections().stream()
        .anyMatch(s -> s.getExplanation() != null && !s.getExplanation().isBlank());
  }

  int generatedCount() {
    return generatedCount.get();
  }

  long firstConsumerNanos() {
    return firstConsumerNanos.get();
  }

  long lastConsumerNanos() {
    return lastConsumerNanos.get();
  }

  /** Phase 2 해설 조립용 저장 시점 컨텍스트. */
  private record PendingProblem(String bloomsLevel, List<SelectionsOfAI> selections) {}
}
