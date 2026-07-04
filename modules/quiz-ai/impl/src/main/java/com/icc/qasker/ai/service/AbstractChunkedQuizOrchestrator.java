package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.PreviousGenerationContext;
import com.icc.qasker.ai.service.support.StreamingJsonArrayExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import com.icc.qasker.global.error.CustomException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * 청크형(MULTIPLE/BLANK/OX) 퀴즈 오케스트레이터의 공통 골격. N문항 요청을 chunk-size 단위로 분할 호출하고, 청크 K(K≥2)에는 직전 누적 문항
 * 요약을 시스템 프롬프트에 주입한다.
 *
 * <p>한 청크가 실패해도 직전 청크까지의 산출물은 보존된다(PRD S3.1).
 *
 * <p>타입별 차이는 {@link #getSupportedType()}, {@link #maxSelectionCount()}, {@link #dedupInstruction()}
 * 세 훅으로만 노출된다.
 */
@Slf4j
public abstract class AbstractChunkedQuizOrchestrator implements QuizTypeOrchestrator {

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QAskerAiProperties aiProperties;

  protected AbstractChunkedQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.aiProperties = aiProperties;
  }

  /** 문항 최대 선택지 수. 초과 문항은 drop 된다 (MULTIPLE/BLANK=4, OX=2). */
  protected abstract int maxSelectionCount();

  /** 청크 K(K≥2) 시스템 프롬프트 꼬리에 붙는 타입별 중복 회피 지침 문구. */
  protected abstract String dedupInstruction();

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long startNanos = System.nanoTime();
    String tag = getSupportedType();

    DoubleAdder totalCost = new DoubleAdder();
    AtomicLong firstNanos = new AtomicLong(0);
    AtomicLong lastNanos = new AtomicLong(0);
    AtomicInteger delivered = new AtomicInteger(0);
    int quizCount = request.quizCount();

    QuizType quizType = QuizType.valueOf(request.strategyValue());
    String baseSystemPrompt = quizType.getSystemGuideLine(request.language());

    // PDF 업로드는 한 번만 (캐시)
    String cacheKey =
        geminiFileService.generateCacheKey(request.fileUrl(), request.referencePages());
    FileMetadata metadata;
    try {
      metadata =
          geminiFileService
              .awaitCachedFileMetadata(cacheKey)
              .orElseGet(
                  () -> geminiFileService.uploadPdf(request.fileUrl(), request.referencePages()));
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiInfraException("PDF 업로드 실패", e);
    }

    List<Integer> chunkPlan = aiProperties.getChunk().planChunks(quizCount);
    log.info(
        "{} 청크 분할: 요청={}문항, chunk-size={}, 청크={}개",
        tag,
        quizCount,
        aiProperties.getChunk().getChunkSize(),
        chunkPlan.size());

    List<AIProblem> accumulated = new ArrayList<>();
    int chunksDone = 0;

    try {
      for (int chunkIndex = 0; chunkIndex < chunkPlan.size(); chunkIndex++) {
        int chunkSize = chunkPlan.get(chunkIndex);
        if (delivered.get() >= quizCount) break;

        String systemPrompt = buildSystemPrompt(baseSystemPrompt, accumulated, chunkIndex);
        String userPrompt =
            quizType.generateRequestPrompt(
                request.referencePages(), chunkSize, request.customInstruction());

        runChunk(
            chunkIndex,
            systemPrompt,
            userPrompt,
            metadata,
            request,
            quizCount,
            chunkSize,
            startNanos,
            delivered,
            firstNanos,
            lastNanos,
            totalCost,
            accumulated);
        chunksDone++;
      }
    } catch (CustomException e) {
      // 정책: 청크 도중 비즈니스 예외가 발생해도 직전 청크 결과는 보존
      if (delivered.get() == 0) throw e;
      log.warn(
          "{} 청크 도중 비즈니스 오류. {}/{} 청크, {}문항 보존.",
          tag,
          chunksDone,
          chunkPlan.size(),
          delivered.get(),
          e);
    } catch (Exception e) {
      if (delivered.get() == 0) throw new GeminiInfraException("Gemini 인프라 장애", e);
      log.warn(
          "{} 청크 도중 인프라 오류. {}/{} 청크, {}문항 보존.",
          tag,
          chunksDone,
          chunkPlan.size(),
          delivered.get(),
          e);
      metricsRecorder.recordStreamingTimeout(tag);
    }

    log.info(
        "{} 청크 루프 완료: 전달={}문항(목표 {}), 청크 완료={}/{}, 총 소요={}ms",
        tag,
        delivered.get(),
        quizCount,
        chunksDone,
        chunkPlan.size(),
        (System.nanoTime() - startNanos) / 1_000_000);

    Long first = firstNanos.get() == 0 ? null : firstNanos.get();
    Long last = lastNanos.get() == 0 ? null : lastNanos.get();
    metricsRecorder.recordRequestDuration(chunksDone, startNanos, first, last, totalCost.sum());
    return chunksDone == 0 ? 1 : chunksDone;
  }

  /** 한 청크의 Gemini 호출 + 스트리밍 파싱을 실행한다. */
  private void runChunk(
      int chunkIndex,
      String systemPrompt,
      String userPrompt,
      FileMetadata metadata,
      GenerationRequestToAI request,
      int quizCount,
      int chunkSize,
      long startNanos,
      AtomicInteger delivered,
      AtomicLong firstNanos,
      AtomicLong lastNanos,
      DoubleAdder totalCost,
      List<AIProblem> accumulated) {

    String tag = getSupportedType();
    var pdfMedia =
        new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));
    var userMessage = UserMessage.builder().text(userPrompt).media(pdfMedia).build();
    var systemMessage = new SystemMessage(systemPrompt);

    String responseSchema = GeminiResponseSchema.forInstruction(request.customInstruction());
    var options =
        GoogleGenAiChatOptions.builder()
            .responseMimeType("application/json")
            .responseSchema(responseSchema)
            .build();

    Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
    log.info(
        "{} 청크 #{} 시작: 청크 목표={}문항, 누적={}/{}",
        tag,
        chunkIndex,
        chunkSize,
        delivered.get(),
        quizCount);

    StreamingJsonArrayExtractor<GeminiQuestion> extractor =
        new StreamingJsonArrayExtractor<>(
            objectMapper,
            GeminiQuestion.class,
            question -> {
              if (delivered.get() >= quizCount) return;
              if (question.selections() != null
                  && question.selections().size() > maxSelectionCount()) return;

              int count = delivered.incrementAndGet();
              var dtoSet = GeminiQuestionMapper.toDto(List.of(question), metadata.sourcePages());
              if (dtoSet.quiz() != null) {
                accumulated.addAll(dtoSet.quiz());
              }
              request.questionsConsumer().accept(dtoSet);

              long now = System.nanoTime();
              firstNanos.compareAndSet(0, now);
              lastNanos.updateAndGet(prev -> Math.max(prev, now));

              if (count == 1) {
                long ttfqMs = (now - startNanos) / 1_000_000;
                log.info("TTFQ (Time To First Question): {}ms", ttfqMs);
              }
            },
            tag);

    Flux<ChatResponse> stream = chatModel.stream(prompt);
    stream
        .doOnNext(
            response -> {
              if (response.getResult() != null
                  && response.getResult().getOutput() != null
                  && response.getResult().getOutput().getText() != null) {
                extractor.feed(response.getResult().getOutput().getText());
              }

              if (response.getMetadata() != null
                  && response.getMetadata().getUsage() != null
                  && response.getMetadata().getUsage().getCompletionTokens() > 0) {
                double cost =
                    metricsRecorder.recordChunkUsage(
                        tag + " chunk #" + chunkIndex,
                        startNanos,
                        response.getMetadata().getUsage());
                totalCost.add(cost);
              }
            })
        .blockLast(java.time.Duration.ofMinutes(6));
  }

  /** 청크 K(K≥2)의 시스템 프롬프트에 직전 누적 컨텍스트와 중복 회피 지침을 부가한다. */
  private String buildSystemPrompt(
      String baseSystemPrompt, List<AIProblem> accumulated, int chunkIndex) {
    if (chunkIndex == 0 || accumulated.isEmpty()) return baseSystemPrompt;

    PreviousGenerationContext ctx = PreviousGenerationContext.from(accumulated);
    if (ctx.isEmpty()) return baseSystemPrompt;

    String summaryJson = serializeContext(ctx);
    return baseSystemPrompt + "\n\n# 직전 청크 누적 문항 요약 (중복 회피용)\n" + summaryJson + dedupInstruction();
  }

  private String serializeContext(PreviousGenerationContext ctx) {
    try {
      return objectMapper.writeValueAsString(ctx);
    } catch (Exception e) {
      log.warn("직전 컨텍스트 직렬화 실패. 컨텍스트 주입 생략.", e);
      return "[]";
    }
  }
}
