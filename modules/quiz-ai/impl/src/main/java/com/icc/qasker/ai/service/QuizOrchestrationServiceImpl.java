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
import com.icc.qasker.ai.structure.GeminiQuestion;
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

  public QuizOrchestrationServiceImpl(
      QAskerAiProperties aiProperties,
      GeminiFileService geminiFileService,
      GeminiCacheService geminiCacheService,
      GeminiChatService geminiChatService,
      GeminiMetricsRecorder metricsRecorder) {
    this.chunkProperties = aiProperties.getChunk();
    this.geminiFileService = geminiFileService;
    this.geminiCacheService = geminiCacheService;
    this.geminiChatService = geminiChatService;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    long requestStartNanos = System.nanoTime();
    AtomicLong firstQuizNanos = new AtomicLong(0);
    AtomicLong lastQuizNanos = new AtomicLong(0);
    DoubleAdder totalCostAdder = new DoubleAdder();

    CacheInfo cacheInfo = null;
    long cacheCreatedAtMs = 0;
    try {
      // Gemini 파일 캐시 확인 → 진행 중이면 대기, 미스 시 기존 방식으로 업로드
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(request.fileUrl())
              .orElseGet(() -> geminiFileService.uploadPdf(request.fileUrl()));

      cacheInfo = geminiCacheService.createCache(metadata.uri(), request.strategyValue());
      cacheCreatedAtMs = System.currentTimeMillis();

      // A/B 테스트: 요청마다 랜덤으로 maxChunkCount 선택
      int maxChunkCount = chunkProperties.pickMaxCount();

      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), maxChunkCount);
      log.info("청크 분할 완료: {}개 청크 (maxChunkCount={})", chunks.size(), maxChunkCount);

      AtomicInteger numberCounter = new AtomicInteger(1);

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        final String finalCacheName = cacheInfo.name();
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        for (ChunkInfo chunk : chunks) {
          CompletableFuture<Void> future =
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      GeminiChatService.ParsedResult parsed =
                          geminiChatService.callAndParse(chunk, finalCacheName);
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

                      // 문제+해설 원본 데이터 전송
                      AIProblemSet result =
                          GeminiQuestionMapper.toDto(
                              validated, chunk.referencedPages(), numberCounter);
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
      log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨", numberCounter.get() - 1);

      // 요청 단위 응답 시간 메트릭 기록 (A/B 테스트 태그 포함)
      Long firstNanos = firstQuizNanos.get() == 0 ? null : firstQuizNanos.get();
      Long lastNanos = lastQuizNanos.get() == 0 ? null : lastQuizNanos.get();
      metricsRecorder.recordRequestDuration(
          maxChunkCount, requestStartNanos, firstNanos, lastNanos, totalCostAdder.sum());

    } catch (Exception e) {
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    } finally {
      if (cacheInfo == null) {
        return;
      }
      if (cacheCreatedAtMs > 0 && cacheInfo.tokenCount() > 0) {
        metricsRecorder.recordCacheStorageCost(
            cacheInfo.tokenCount(), System.currentTimeMillis() - cacheCreatedAtMs);
      }
      // 캐시 안지우면 큰일남
      geminiCacheService.deleteCache(cacheInfo.name());
    }
  }
}
