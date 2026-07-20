package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.GENERATING;
import static com.icc.qasker.quizset.GenerationStatus.PROBLEMS_READY;

import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizmake.mapper.AIProblemSetMapper;
import com.icc.qasker.quizmake.mapper.ExplanationMarkdownBuilder;
import com.icc.qasker.quizset.QualityLogService;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.QualityLogEntry;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.view.QuizView;
import com.icc.qasker.quizset.view.QuizViewToQuizForFeMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 배치 인터리빙 생성(Q9)의 저장 콜백 구현. 오케스트레이터가 Phase 1(문제)·Phase 2(해설)를 배치 단위로 교차 호출한다.
 *
 * <ul>
 *   <li>{@link #saveProblem}: 문제 1건을 저장하고 SSE로 통지한다. 선지는 오케스트레이터가 이미 최종 순서로 정렬했으므로 재정렬하지 않는다. 선지에
 *       해설이 인라인으로 포함된 단일 호출 타입(ESSAY)은 이 시점에 해설 마크다운까지 조립·저장한다.
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final AtomicInteger generatedCount = new AtomicInteger(0);
  private final AtomicInteger numberCounter = new AtomicInteger(1);
  private final AtomicLong firstConsumerNanos = new AtomicLong(0);
  private final AtomicLong lastConsumerNanos = new AtomicLong(0);
  private final ReentrantLock consumerLock = new ReentrantLock();

  GenerationBatchConsumer(
      String sessionId,
      Long problemSetId,
      GenerationRequest request,
      QuizCommandService quizCommandService,
      QuizQueryService quizQueryService,
      SseNotificationService notificationService,
      HashUtil hashUtil,
      QualityLogService qualityLogService) {
    this.sessionId = sessionId;
    this.problemSetId = problemSetId;
    this.request = request;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.notificationService = notificationService;
    this.hashUtil = hashUtil;
    this.qualityLogService = qualityLogService;
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

      sendCreated(savedNumbers);

      generatedCount.incrementAndGet();
      firstConsumerNanos.compareAndSet(0, System.nanoTime());
      lastConsumerNanos.set(System.nanoTime());
      return number;
    } finally {
      consumerLock.unlock();
    }
  }

  @Override
  public void recordV1(int number, AIProblem v1, String v1Feedback) {
    // 첫 생성본(v1)을 품질 로그에 write-once 기록. 서빙(problem)과 분리된 별도 트랜잭션 best-effort — 실패해도 전파하지 않는다.
    try {
      QuizGeneratedFromAI quiz = AIProblemSetMapper.toQuiz(v1);
      String explanation = ExplanationMarkdownBuilder.build(quiz, request.language());
      String questionJson = serializeQuestion(v1);
      qualityLogService.upsertV1(
          new QualityLogEntry(problemSetId, number, questionJson, explanation, v1Feedback));
    } catch (Exception e) {
      log.warn("[품질 로그] v1 기록 실패 problemSetId={} number={}", problemSetId, number, e);
    }
  }

  @Override
  public void recordV2(int number, AIProblem v2) {
    // 재생성 개선본(v2)을 품질 로그 행에 부착. best-effort — 실패해도 전파하지 않는다.
    try {
      QuizGeneratedFromAI quiz = AIProblemSetMapper.toQuiz(v2);
      String explanation = ExplanationMarkdownBuilder.build(quiz, request.language());
      String questionJson = serializeQuestion(v2);
      qualityLogService.attachV2(problemSetId, number, questionJson, explanation);
    } catch (Exception e) {
      log.warn("[품질 로그] v2 부착 실패 problemSetId={} number={}", problemSetId, number, e);
    }
  }

  /** 문항 질문(stem+선지+적용 지시)을 JSON으로 직렬화한다. Pass-2가 로그만으로 검증 요청을 재구성할 수 있게 한다. */
  private String serializeQuestion(AIProblem problem) {
    return objectMapper.writeValueAsString(
        Map.of(
            "stem",
            problem.content() == null ? "" : problem.content(),
            "selections",
            problem.selections() == null ? List.of() : problem.selections(),
            "appliedInstruction",
            problem.appliedInstruction() == null ? "" : problem.appliedInstruction()));
  }

  @Override
  public void markProblemsReady() {
    quizCommandService.updateStatus(problemSetId, PROBLEMS_READY);
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
}
