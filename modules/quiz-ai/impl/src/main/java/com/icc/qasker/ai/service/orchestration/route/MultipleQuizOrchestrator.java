package com.icc.qasker.ai.service.orchestration.route;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.GeminiCacheService;
import com.icc.qasker.ai.service.GeminiCacheService.CacheInfo;
import com.icc.qasker.ai.service.GeminiChatService;
import com.icc.qasker.ai.service.GeminiChatService.ParsedResult;
import com.icc.qasker.ai.service.orchestration.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.QuizRewriter;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.util.ChunkSplitter;
import com.icc.qasker.global.error.CustomException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 객관식(MULTIPLE) 퀴즈 오케스트레이터. generate → rewrite → deliver 파이프라인. */
@Slf4j
@Component
public class MultipleQuizOrchestrator implements QuizTypeOrchestrator {

  private static final int MAX_SELECTION_COUNT = 4;

  private final QAskerAiProperties.Chunk chunkProperties;
  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final GeminiChatService geminiChatService;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QuizRewriter quizRewriter;

  public MultipleQuizOrchestrator(
      QAskerAiProperties aiProperties,
      GeminiFileService geminiFileService,
      GeminiCacheService geminiCacheService,
      GeminiChatService geminiChatService,
      GeminiMetricsRecorder metricsRecorder,
      QuizRewriter quizRewriter) {
    this.chunkProperties = aiProperties.getChunk();
    this.geminiFileService = geminiFileService;
    this.geminiCacheService = geminiCacheService;
    this.geminiChatService = geminiChatService;
    this.metricsRecorder = metricsRecorder;
    this.quizRewriter = quizRewriter;
  }

  @Override
  public String getSupportedType() {
    return "MULTIPLE";
  }

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long startNanos = System.nanoTime();
    int maxChunkCount = 0;
    CacheInfo cacheInfo = null;

    AtomicInteger remainingQuota = new AtomicInteger(request.quizCount());
    DoubleAdder totalCost = new DoubleAdder();
    AtomicLong firstNanos = new AtomicLong(0);
    AtomicLong lastNanos = new AtomicLong(0);

    try {
      // 파일 업로드 + 캐시
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(request.fileUrl())
              .orElseGet(() -> geminiFileService.uploadPdf(request.fileUrl()));
      cacheInfo =
          geminiCacheService.createCache(
              metadata.uri(), request.strategyValue(), request.language());
      String cacheName = cacheInfo.name();

      // 청크 분할
      maxChunkCount = chunkProperties.pickMaxCount();
      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), maxChunkCount);
      log.info("청크 분할 완료: {}개 청크 (maxChunkCount={})", chunks.size(), maxChunkCount);

      // 청크별 병렬 파이프라인: generate → rewrite → deliver
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            chunks.stream()
                .map(
                    chunk ->
                        CompletableFuture.runAsync(
                            () -> {
                              try {
                                processChunk(
                                    chunk,
                                    cacheName,
                                    request,
                                    remainingQuota,
                                    totalCost,
                                    firstNanos,
                                    lastNanos);
                              } catch (Exception e) {
                                log.error(
                                    "청크 처리 실패 (계속 진행): pages={}, error={}",
                                    chunk.referencedPages(),
                                    e.getMessage());
                              }
                            },
                            executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      log.info("전체 병렬 생성 완료");

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(
          maxChunkCount, startNanos, first, last, totalCost.sum());
      return maxChunkCount;

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    } finally {
      if (cacheInfo != null) {
        geminiCacheService.deleteCache(cacheInfo.name());
      }
    }
  }

  private void processChunk(
      ChunkInfo chunk,
      String cacheName,
      GenerationRequestToAI request,
      AtomicInteger remainingQuota,
      DoubleAdder totalCost,
      AtomicLong firstNanos,
      AtomicLong lastNanos)
      throws Exception {
    // Step 1: 초안 생성
    ParsedResult parsed =
        geminiChatService.callAndParse(
            chunk, cacheName, request.strategyValue(), request.language(), null);
    if (parsed == null
        || parsed.response() == null
        || CollectionUtils.isEmpty(parsed.response().questions())) {
      return;
    }

    List<GeminiQuestion> questions =
        parsed.response().questions().stream()
            .filter(q -> q.selections() == null || q.selections().size() <= MAX_SELECTION_COUNT)
            .toList();
    if (questions.isEmpty()) {
      log.warn("유효한 문제가 존재하지 않습니다: pages={}", chunk.referencedPages());
      return;
    }
    double cost = parsed.cost();

    // Step 2: 리라이트 (정보누출 교정 + 선택지 균등화)
    QuizRewriter.RewriteResult rewritten =
        quizRewriter.rewrite(questions, cacheName, request.language());
    if (rewritten.questions() != null && !rewritten.questions().isEmpty()) {
      questions = rewritten.questions();
    }
    cost += rewritten.cost();

    // Step 3: 할당량 확보 + 전달
    totalCost.add(cost);
    int size = questions.size();
    int before = remainingQuota.getAndUpdate(r -> Math.max(0, r - size));
    int claimed = Math.min(size, before);
    if (claimed == 0) return;
    if (claimed < size) {
      questions = questions.subList(0, claimed);
    }

    request.questionsConsumer().accept(GeminiQuestionMapper.toDto(questions));
    long now = System.nanoTime();
    firstNanos.compareAndSet(0, now);
    lastNanos.updateAndGet(prev -> Math.max(prev, now));
  }
}
