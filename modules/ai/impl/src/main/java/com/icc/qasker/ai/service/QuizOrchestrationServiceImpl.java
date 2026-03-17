package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIExplanationUpdate;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.mapper.SplitExplanationMarkdownBuilder;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.streaming.StreamingJsonSplitParser;
import com.icc.qasker.ai.structure.GeminiExplanationEntry;
import com.icc.qasker.ai.structure.GeminiQuestionEntry;
import com.icc.qasker.ai.structure.GeminiSplitResponse;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final ChatModel chatModel;

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    FileMetadata metadata = geminiFileService.uploadPdf(request.fileUrl());
    log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

    var converter = new BeanOutputConverter<>(GeminiSplitResponse.class);
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

      // chunk-local number → global number 매핑
      Map<Integer, Integer> numberMapping = new HashMap<>();

      StreamingJsonSplitParser parser =
          new StreamingJsonSplitParser(
              // onQuestionsReady: 선택지 검증 → 번호 할당 → questionsConsumer 호출
              questions -> {
                List<GeminiQuestionEntry> validated =
                    questions.stream()
                        .filter(
                            q ->
                                q.selections() == null
                                    || q.selections().size() <= MAX_SELECTION_COUNT)
                        .toList();

                if (validated.isEmpty()) {
                  log.warn("유효한 문제 없음: pages={}", chunk.referencedPages());
                  return;
                }

                AIProblemSet result =
                    GeminiQuestionMapper.toDto(
                        validated, chunk.referencedPages(), numberCounter, numberMapping);
                request.questionsConsumer().accept(result);

                log.debug(
                    "청크 questions 전송: pages={}, 문제 {}개",
                    chunk.referencedPages(),
                    result.quiz().size());
              },
              // onFullResponseReady: explanations 변환 → explanationsConsumer 호출
              fullResponse -> {
                if (fullResponse.explanations() != null && !fullResponse.explanations().isEmpty()) {
                  List<AIExplanationUpdate> updates =
                      toExplanationUpdates(fullResponse, numberMapping);
                  request.explanationsConsumer().accept(updates);

                  log.debug(
                      "청크 explanations 전송: pages={}, 해설 {}개",
                      chunk.referencedPages(),
                      updates.size());
                }
              },
              // onError: 에러 전파
              ex -> {
                log.error(
                    "청크 스트리밍 파서 에러: pages={}, error={}", chunk.referencedPages(), ex.getMessage());
                request.errorConsumer().accept(ex);
              });

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
                  parser.onChunk(text);
                }
              })
          .doOnComplete(parser::onStreamComplete)
          .doOnError(
              ex ->
                  parser.onStreamError(
                      ex instanceof Exception ? (Exception) ex : new RuntimeException(ex)))
          .blockLast();
    } catch (Exception e) {
      log.error("청크 처리 실패 (계속 진행): pages={}, error={}", chunk.referencedPages(), e.getMessage());
    }
  }

  /**
   * GeminiSplitResponse의 explanations를 AIExplanationUpdate 목록으로 변환한다.
   *
   * @param response 전체 파싱 결과
   * @param numberMapping chunk-local → global 번호 매핑
   * @return 변환된 해설 업데이트 목록
   */
  private List<AIExplanationUpdate> toExplanationUpdates(
      GeminiSplitResponse response, Map<Integer, Integer> numberMapping) {
    // questions를 number로 인덱싱 (선택지 정보 참조용)
    Map<Integer, GeminiQuestionEntry> questionByNumber =
        response.questions().stream()
            .collect(Collectors.toMap(GeminiQuestionEntry::number, Function.identity()));

    List<AIExplanationUpdate> updates = new ArrayList<>();
    for (GeminiExplanationEntry exp : response.explanations()) {
      Integer globalNumber = numberMapping.get(exp.number());
      if (globalNumber == null) {
        log.warn("해설의 번호에 대응하는 문제가 없음: chunkNumber={}", exp.number());
        continue;
      }

      GeminiQuestionEntry question = questionByNumber.get(exp.number());
      String merged = SplitExplanationMarkdownBuilder.build(exp, question);
      updates.add(new AIExplanationUpdate(globalNumber, merged));
    }
    return updates;
  }
}
