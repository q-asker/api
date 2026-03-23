package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@AllArgsConstructor
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

  private static final int MAX_CHUNK_COUNT = 10;
  private static final int MAX_SELECTION_COUNT = 4;

  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final GeminiChatService geminiChatService;

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    // Gemini 파일 캐시 확인 → 진행 중이면 대기, 미스 시 기존 방식으로 업로드
    FileMetadata metadata =
        geminiFileService
            .awaitCachedFileMetadata(request.fileUrl())
            .orElseGet(
                () -> {
                  log.info("Gemini 파일 캐시 미스, 업로드 시작: {}", request.fileUrl());
                  return geminiFileService.uploadPdf(request.fileUrl());
                });
    log.info("Gemini 파일 준비 완료: name={}, uri={}", metadata.name(), metadata.uri());

    GeminiCacheService.CacheInfo cacheInfo = null;
    Instant cacheCreatedAt = null;
    try {
      cacheInfo = geminiCacheService.createCache(metadata.uri(), request.strategyValue());
      cacheCreatedAt = Instant.now();
      log.info("캐시 생성 완료: cacheName={}, tokenCount={}", cacheInfo.name(), cacheInfo.tokenCount());

      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), MAX_CHUNK_COUNT);
      log.info("청크 분할 완료: {}개 청크", chunks.size());

      AtomicInteger numberCounter = new AtomicInteger(1);

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        final String finalCacheName = cacheInfo.name();
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        for (ChunkInfo chunk : chunks) {
          CompletableFuture<Void> future =
              CompletableFuture.runAsync(
                  () -> processChunk(request, chunk, finalCacheName, numberCounter), executor);
          futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨", numberCounter.get() - 1);
    } finally {
      if (cacheInfo != null) {
        // 캐시 저장 비용 추정: $1.00/1M tokens/hour
        if (cacheCreatedAt != null && cacheInfo.tokenCount() > 0) {
          double durationHours =
              Duration.between(cacheCreatedAt, Instant.now()).toMillis() / 3_600_000.0;
          double storageCost = cacheInfo.tokenCount() * 1.00 / 1_000_000 * durationHours;
          log.info(
              "캐시 저장 비용 추정 - 캐시 토큰: {}, 사용 시간: {}분, 추정 비용: ${}",
              cacheInfo.tokenCount(),
              String.format("%.1f", durationHours * 60),
              String.format("%.6f", storageCost));
        }
        geminiCacheService.deleteCache(cacheInfo.name());
      }
      // Gemini 파일은 삭제하지 않음 — 48시간 자동 만료, Caffeine 캐시로 재사용 가능
    }
  }

  private void processChunk(
      GenerationRequestToAI request,
      ChunkInfo chunk,
      String cacheName,
      AtomicInteger numberCounter) {
    try {
      log.debug("청크 처리 시작: pages={}, quizCount={}", chunk.referencedPages(), chunk.quizCount());

      GeminiResponse response = geminiChatService.callAndParse(chunk, cacheName);
      if (response == null) {
        return;
      }

      processResponse(request, chunk, response, numberCounter);
    } catch (Exception e) {
      log.error("청크 처리 실패 (계속 진행): pages={}, error={}", chunk.referencedPages(), e.getMessage());
      request.errorConsumer().accept(e);
    }
  }

  private void processResponse(
      GenerationRequestToAI request,
      ChunkInfo chunk,
      GeminiResponse response,
      AtomicInteger numberCounter) {
    if (CollectionUtils.isEmpty(response.questions())) {
      log.warn("응답에 문제가 없습니다: pages={}", chunk.referencedPages());
      return;
    }

    List<GeminiQuestion> validated =
        response.questions().stream()
            .filter(q -> q.selections() == null || q.selections().size() <= MAX_SELECTION_COUNT)
            .toList();

    if (validated.isEmpty()) {
      log.warn("유효한 문제 없음: pages={}", chunk.referencedPages());
      return;
    }

    // 문제+해설 원본 데이터 전송
    AIProblemSet result =
        GeminiQuestionMapper.toDto(validated, chunk.referencedPages(), numberCounter);
    request.questionsConsumer().accept(result);
    log.debug("청크 전송 완료: pages={}, 문제 {}개", chunk.referencedPages(), result.quiz().size());
  }
}
