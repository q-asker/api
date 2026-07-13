package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.GENERATING;

import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizmake.mapper.AIProblemSetMapper;
import com.icc.qasker.quizmake.mapper.ExplanationMarkdownBuilder;
import com.icc.qasker.quizmake.mapper.SelectionArranger;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quizset.view.QuizView;
import com.icc.qasker.quizset.view.QuizViewToQuizForFeMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * AI가 스트리밍으로 전달하는 배치(문제 묶음) 1건을 처리하는 소비자.
 *
 * <p>변환 → 선택지 배치 → 마크다운 조립 → 순차 번호 부여 → 영속화 → SSE 전송 → 타이밍/카운터 누적을 담당한다. 여러 배치가 동시에 도착해도 순서와 번호가
 * 어긋나지 않도록 {@link ReentrantLock}으로 직렬화한다.
 */
@Slf4j
class GenerationBatchConsumer implements Consumer<AIProblemSet> {

  private final String sessionId;
  private final Long problemSetId;
  private final GenerationRequest request;
  private final QuizCommandService quizCommandService;
  private final QuizQueryService quizQueryService;
  private final SseNotificationService notificationService;
  private final HashUtil hashUtil;

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
      HashUtil hashUtil) {
    this.sessionId = sessionId;
    this.problemSetId = problemSetId;
    this.request = request;
    this.quizCommandService = quizCommandService;
    this.quizQueryService = quizQueryService;
    this.notificationService = notificationService;
    this.hashUtil = hashUtil;
  }

  @Override
  public void accept(AIProblemSet aiProblemSet) {
    consumerLock.lock();
    try {
      // 1. 전송용 DTO로 변환
      ProblemSetGeneratedEvent problemSet = AIProblemSetMapper.toEvent(aiProblemSet);
      if (CollectionUtils.isEmpty(problemSet.getQuiz())) {
        log.warn("[AI 생성 스킵] 빈 배치 수신 sessionId={}", sessionId);
        return;
      }

      // 2. 선택지 배치 (셔플 / OX 정규화)
      SelectionArranger.arrange(request.quizType(), problemSet.getQuiz());

      // 3. 마크다운 포맷팅
      for (QuizGeneratedFromAI quiz : problemSet.getQuiz()) {
        quiz.setExplanation(ExplanationMarkdownBuilder.build(quiz, request.language()));
      }

      // 4. 순서 보장 번호 할당
      for (QuizGeneratedFromAI quiz : problemSet.getQuiz()) {
        quiz.setNumber(numberCounter.getAndIncrement());
      }

      // 5. 데이터베이스에 영속화
      List<Integer> savedNumbers = quizCommandService.saveBatch(problemSet.getQuiz(), problemSetId);

      // 6. SSE 전송
      List<QuizView> quizViews = quizQueryService.getQuizViews(problemSetId, savedNumbers);
      if (quizViews.isEmpty()) {
        return;
      }

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

      generatedCount.addAndGet(quizViews.size());
      firstConsumerNanos.compareAndSet(0, System.nanoTime());
      lastConsumerNanos.set(System.nanoTime());
    } finally {
      consumerLock.unlock();
    }
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
