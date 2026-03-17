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
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

  private static final int MAX_CHUNK_COUNT = 10;
  private static final int MAX_SELECTION_COUNT = 4;

  private final ObjectMapper objectMapper;
  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final ChatModel chatModel;

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    FileMetadata metadata = geminiFileService.uploadPdf(request.fileUrl());
    log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

    var converter = new BeanOutputConverter<>(GeminiResponse.class);
    String jsonSchema = converter.getJsonSchema();

    String cacheName = null;
    try {
      cacheName =
          geminiCacheService.createCache(metadata.uri(), request.strategyValue(), jsonSchema);
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
                  () -> processChunkStreaming(request, chunk, finalCacheName, numberCounter),
                  executor);
          futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨", numberCounter.get() - 1);
    } finally {
      if (cacheName != null) {
        geminiCacheService.deleteCache(cacheName);
      }
      geminiFileService.deleteFile(metadata.name());
    }
  }

  private void processChunkStreaming(
      GenerationRequestToAI request,
      ChunkInfo chunk,
      String cacheName,
      AtomicInteger numberCounter) {
    try {
      log.debug("청크 처리 시작: pages={}, quizCount={}", chunk.referencedPages(), chunk.quizCount());

      String userPrompt = UserPrompt.generate(chunk.referencedPages(), chunk.quizCount());
      StringBuilder buffer = new StringBuilder();

      Prompt prompt =
          new Prompt(
              userPrompt,
              GoogleGenAiChatOptions.builder()
                  .useCachedContent(true)
                  .cachedContentName(cacheName)
                  .responseMimeType("application/json")
                  .build());

      chatModel.stream(prompt)
          .doOnNext(
              chatResponse -> {
                String text = chatResponse.getResult().getOutput().getText();
                if (text != null) {
                  buffer.append(text);
                }
              })
          .doOnComplete(
              () -> {
                String json = buffer.toString().trim();
                if (json.isEmpty()) {
                  log.error("스트리밍 응답이 비어있습니다: pages={}", chunk.referencedPages());
                  return;
                }
                try {
                  GeminiResponse response = objectMapper.readValue(json, GeminiResponse.class);
                  processResponse(request, chunk, response, numberCounter);
                } catch (Exception e) {
                  log.error(
                      "응답 JSON 파싱 실패: pages={}, error={}", chunk.referencedPages(), e.getMessage());
                  request.errorConsumer().accept(e);
                }
              })
          .doOnError(
              ex -> {
                log.error("스트리밍 에러: pages={}, error={}", chunk.referencedPages(), ex.getMessage());
                request
                    .errorConsumer()
                    .accept(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
              })
          .blockLast();
    } catch (Exception e) {
      log.error("청크 처리 실패 (계속 진행): pages={}, error={}", chunk.referencedPages(), e.getMessage());
    }
  }

  private void processResponse(
      GenerationRequestToAI request,
      ChunkInfo chunk,
      GeminiResponse response,
      AtomicInteger numberCounter) {
    if (response.questions() == null || response.questions().isEmpty()) {
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
