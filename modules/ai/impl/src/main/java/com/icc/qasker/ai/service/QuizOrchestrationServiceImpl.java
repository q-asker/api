package com.icc.qasker.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.prompt.user.UserPrompt;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

  private static final int MAX_CHUNK_COUNT = 10;
  private static final int MAX_SELECTION_COUNT = 4;
  private static final String RESPONSE_JSON_SCHEMA =
      new BeanOutputConverter<>(GeminiResponse.class).getJsonSchema();

  private final ObjectMapper objectMapper;
  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final ChatModel chatModel;

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

    String cacheName = null;
    try {
      cacheName = geminiCacheService.createCache(metadata.uri(), request.strategyValue());
      log.info("캐시 생성 완료: cacheName={}", cacheName);

      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), MAX_CHUNK_COUNT);
      log.info("청크 분할 완료: {}개 청크", chunks.size());

      AtomicInteger numberCounter = new AtomicInteger(1);

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        final String finalCacheName = cacheName;
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
      if (cacheName != null) {
        geminiCacheService.deleteCache(cacheName);
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

      String userPrompt = UserPrompt.generate(chunk.referencedPages(), chunk.quizCount());

      Prompt prompt =
          new Prompt(
              userPrompt,
              GoogleGenAiChatOptions.builder()
                  .useCachedContent(true)
                  .cachedContentName(cacheName)
                  .responseMimeType("application/json")
                  .responseSchema(RESPONSE_JSON_SCHEMA)
                  .build());

      ChatResponse chatResponse = chatModel.call(prompt);

      // usage 메타데이터 로깅
      if (chatResponse.getMetadata().getUsage() != null) {
        var usage = chatResponse.getMetadata().getUsage();
        log.info(
            "Gemini Usage - 입력: {}토큰, 출력: {}토큰, 총: {}토큰, 메타데이터: {}",
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens(),
            chatResponse.getMetadata());
      }

      String json = chatResponse.getResult().getOutput().getText();
      log.info("생성된 퀴즈 원본: {}", json);
      if (json == null || json.isBlank()) {
        log.error("응답이 비어있습니다: pages={}", chunk.referencedPages());
        return;
      }

      GeminiResponse response = objectMapper.readValue(json.trim(), GeminiResponse.class);
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

    // 문제+해설 원본 데이터 전송 (마크다운 빌드는 quiz 모듈에서 셔플 후 수행)
    AIProblemSet result =
        GeminiQuestionMapper.toDto(validated, chunk.referencedPages(), numberCounter);
    request.questionsConsumer().accept(result);
    log.debug("청크 전송 완료: pages={}, 문제 {}개", chunk.referencedPages(), result.quiz().size());
  }
}
