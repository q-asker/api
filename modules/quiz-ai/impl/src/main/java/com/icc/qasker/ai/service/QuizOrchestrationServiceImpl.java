package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiCacheService.CacheInfo;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.support.GeminiChatService;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.SelectionEqualizer;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiQuestion.GeminiSelection;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

  private static final int MAX_SELECTION_COUNT = 4;

  private final QAskerAiProperties.Chunk chunkProperties;
  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final GeminiChatService geminiChatService;
  private final GeminiMetricsRecorder metricsRecorder;
  private final SelectionEqualizer selectionEqualizer;

  public QuizOrchestrationServiceImpl(
      QAskerAiProperties aiProperties,
      GeminiFileService geminiFileService,
      GeminiCacheService geminiCacheService,
      GeminiChatService geminiChatService,
      GeminiMetricsRecorder metricsRecorder,
      SelectionEqualizer selectionEqualizer) {
    this.chunkProperties = aiProperties.getChunk();
    this.geminiFileService = geminiFileService;
    this.geminiCacheService = geminiCacheService;
    this.geminiChatService = geminiChatService;
    this.metricsRecorder = metricsRecorder;
    this.selectionEqualizer = selectionEqualizer;
  }

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long requestStartNanos = System.nanoTime();
    AtomicLong firstQuizNanos = new AtomicLong(0);
    AtomicLong lastQuizNanos = new AtomicLong(0);
    DoubleAdder totalCostAdder = new DoubleAdder();

    int maxChunkCount = 0;
    CacheInfo cacheInfo = null;
    try {
      // Gemini 파일 캐시 확인 → 진행 중이면 대기, 미스 시 기존 방식으로 업로드
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(request.fileUrl())
              .orElseGet(() -> geminiFileService.uploadPdf(request.fileUrl()));

      cacheInfo =
          geminiCacheService.createCache(
              metadata.uri(), request.strategyValue(), request.language());

      // A/B 테스트: 요청마다 랜덤으로 maxChunkCount 선택
      maxChunkCount = chunkProperties.pickMaxCount();

      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), maxChunkCount);
      log.info("청크 분할 완료: {}개 청크 (maxChunkCount={})", chunks.size(), maxChunkCount);

      AtomicInteger remainingQuota = new AtomicInteger(request.quizCount());

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        final String finalCacheName = cacheInfo.name();
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        for (ChunkInfo chunk : chunks) {
          CompletableFuture<Void> future =
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      GeminiChatService.ParsedResult parsed =
                          geminiChatService.callAndParse(
                              chunk, finalCacheName, request.strategyValue(), request.language());
                      if (parsed == null) {
                        return;
                      }
                      totalCostAdder.add(parsed.cost());
                      GeminiResponse response = parsed.response();
                      if (response == null) {
                        return;
                      }

                      if (CollectionUtils.isEmpty(response.questions())) {
                        return;
                      }

                      List<GeminiQuestion> validated =
                          response.questions().stream()
                              .filter(
                                  q ->
                                      q.selections() == null
                                          || q.selections().size() <= MAX_SELECTION_COUNT)
                              .toList();

                      if (validated.isEmpty()) {
                        log.warn("유효한 문제가 존재하지 않습니다: pages={}", chunk.referencedPages());
                        return;
                      }

                      // 선택지 길이 균등화: MULTIPLE 타입만 적용
                      if ("MULTIPLE".equals(request.strategyValue())) {
                        metricsRecorder.incrementSelectionChecked(validated.size());
                        EqualizeOutcome eqOutcome =
                            equalizeSelectionLengths(validated, request.language());
                        validated = eqOutcome.questions();
                        totalCostAdder.add(eqOutcome.cost());
                      }

                      // 총 요청 수 초과분 제거: CAS로 슬롯을 원자적으로 확보
                      int validatedSize = validated.size();
                      int before = remainingQuota.getAndUpdate(r -> Math.max(0, r - validatedSize));
                      int claimed = Math.min(validatedSize, before);
                      if (claimed == 0) {
                        log.info(
                            "이미 요청 수({})만큼 생성 완료, 초과분 버림: pages={}",
                            request.quizCount(),
                            chunk.referencedPages());
                        return;
                      }
                      if (claimed < validatedSize) {
                        log.info(
                            "초과 문제 {}개 제거: pages={}",
                            validatedSize - claimed,
                            chunk.referencedPages());
                        validated = validated.subList(0, claimed);
                      }

                      // 문제+해설 원본 데이터 전송 (번호는 소비자 측에서 할당)
                      AIProblemSet result = GeminiQuestionMapper.toDto(validated);
                      request.questionsConsumer().accept(result);

                      // 첫 번째/마지막 퀴즈 응답 시각 기록
                      long now = System.nanoTime();
                      firstQuizNanos.compareAndSet(0, now);
                      lastQuizNanos.updateAndGet(prev -> Math.max(prev, now));
                    } catch (Exception e) {
                      log.error(
                          "청크 처리 실패 (계속 진행): pages={}, error={}",
                          chunk.referencedPages(),
                          e.getMessage());
                    }
                  },
                  executor);
          futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      log.info("전체 병렬 생성 완료");

      // 요청 단위 응답 시간 메트릭 기록 (A/B 테스트 태그 포함)
      Long firstNanos = firstQuizNanos.get() == 0 ? null : firstQuizNanos.get();
      Long lastNanos = lastQuizNanos.get() == 0 ? null : lastQuizNanos.get();
      metricsRecorder.recordRequestDuration(
          maxChunkCount, requestStartNanos, firstNanos, lastNanos, totalCostAdder.sum());

    } catch (com.icc.qasker.global.error.CustomException e) {
      // 클라이언트 오류(파일 없음 등)는 서킷브레이커 대상에서 제외 — GeminiInfraException으로 감싸지 않고 그대로 전파
      throw e;
    } catch (Exception e) {
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    } finally {
      if (cacheInfo != null) {
        // 캐시 안지우면 큰일남
        geminiCacheService.deleteCache(cacheInfo.name());
      }
    }
    return maxChunkCount;
  }

  /**
   * 정답이 유일한 최장 선택지인 문항의 선택지 content를 균등화한다. correct/explanation은 원본을 유지한다.
   *
   * @return 균등화된 문항 목록 + 균등화 API 호출 비용 합계
   */
  private EqualizeOutcome equalizeSelectionLengths(
      List<GeminiQuestion> questions, String language) {
    List<GeminiQuestion> result = new ArrayList<>(questions);
    double totalEqualizeCost = 0.0;
    int equalizedCount = 0;

    for (int i = 0; i < result.size(); i++) {
      GeminiQuestion q = result.get(i);
      if (!isCorrectLongest(q)) {
        continue;
      }
      List<String> contents = q.selections().stream().map(GeminiSelection::content).toList();
      SelectionEqualizer.EqualizeResult eqResult = selectionEqualizer.equalize(contents, language);
      if (eqResult == null) {
        continue;
      }
      metricsRecorder.recordEqualization(
          eqResult.inputTokens(), eqResult.outputTokens(), eqResult.cost());
      totalEqualizeCost += eqResult.cost();
      equalizedCount++;
      List<GeminiSelection> newSels = new ArrayList<>();
      for (int j = 0; j < q.selections().size(); j++) {
        GeminiSelection orig = q.selections().get(j);
        newSels.add(
            new GeminiSelection(eqResult.contents().get(j), orig.correct(), orig.explanation()));
      }
      result.set(
          i, new GeminiQuestion(q.content(), newSels, q.quizExplanation(), q.referencedPages()));
    }

    if (equalizedCount > 0) {
      log.info(
          "선택지 균등화 {}건 완료, 균등화 비용: ${}", equalizedCount, String.format("%.6f", totalEqualizeCost));
    }
    return new EqualizeOutcome(result, totalEqualizeCost);
  }

  record EqualizeOutcome(List<GeminiQuestion> questions, double cost) {}

  /** 단일 문항에서 정답이 유일한 최장 선택지인지 검사한다. */
  private boolean isCorrectLongest(GeminiQuestion question) {
    if (question.selections() == null || question.selections().size() < 2) {
      return false;
    }
    int correctLength = 0;
    int maxWrongLength = 0;
    for (GeminiSelection sel : question.selections()) {
      int len = sel.content() != null ? sel.content().length() : 0;
      if (sel.correct()) {
        correctLength = len;
      } else {
        maxWrongLength = Math.max(maxWrongLength, len);
      }
    }
    return correctLength > maxWrongLength && maxWrongLength > 0;
  }
}
