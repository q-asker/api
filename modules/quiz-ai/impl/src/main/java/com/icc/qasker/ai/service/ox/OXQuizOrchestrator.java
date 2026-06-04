package com.icc.qasker.ai.service.ox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingQuestionExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import com.icc.qasker.cost.AiCostRecorder;
import com.icc.qasker.cost.dto.AiInvocationCommand;
import com.icc.qasker.cost.dto.InvocationStatus;
import com.icc.qasker.global.error.CustomException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

/**
 * OX 퀴즈 오케스트레이터. 1회 호출 + 응답 스트리밍으로 문항이 완성될 때마다 즉시 SSE 전달한다.
 *
 * <p>단일 LLM 호출이므로 청크 간 소재 중복이 원천적으로 불가능하다. 캐시 없이 PDF를 직접 참조한다.
 */
@Slf4j
@Component
public class OXQuizOrchestrator implements QuizTypeOrchestrator {

  private static final int MAX_SELECTION_COUNT = 2;

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final AiCostRecorder aiCostRecorder;
  private final String configuredModel;

  public OXQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      AiCostRecorder aiCostRecorder,
      @Value("${spring.ai.google.genai.chat.options.model}") String configuredModel) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.aiCostRecorder = aiCostRecorder;
    this.configuredModel = configuredModel;
  }

  @Override
  public String getSupportedType() {
    return "OX";
  }

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long startNanos = System.nanoTime();

    // AI 호출 1회당 멱등 키 — multi-chunk usage·재시도로 인한 원장 중복(이중 과금)을 막는다(같은 키 재적재는 DB가 차단)
    String requestId = UUID.randomUUID().toString();
    // 비용 적재 완료 여부 — 성공 적재 후 실패 경로의 중복 적재를 막는다
    AtomicBoolean costRecorded = new AtomicBoolean(false);

    DoubleAdder totalCost = new DoubleAdder();
    AtomicLong firstNanos = new AtomicLong(0);
    AtomicLong lastNanos = new AtomicLong(0);
    AtomicInteger delivered = new AtomicInteger(0);
    int quizCount = request.quizCount();

    try {
      // PDF 업로드 (페이지가 지정되면 슬라이싱하여 업로드)
      String cacheKey =
          geminiFileService.generateCacheKey(request.fileUrl(), request.referencePages());
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(cacheKey)
              .orElseGet(
                  () -> geminiFileService.uploadPdf(request.fileUrl(), request.referencePages()));

      // 시스템 프롬프트 + PDF Media + 유저 프롬프트 구성
      QuizType quizType = QuizType.valueOf(request.strategyValue());
      String systemGuideLine = quizType.getSystemGuideLine(request.language());
      String userPrompt =
          quizType.generateRequestPrompt(
              request.referencePages(), quizCount, request.customInstruction());

      SystemMessage systemMessage = new SystemMessage(systemGuideLine);
      Media pdfMedia =
          new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));
      UserMessage userMessage = UserMessage.builder().text(userPrompt).media(pdfMedia).build();

      String responseSchema = GeminiResponseSchema.forInstruction(request.customInstruction());
      var options =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(responseSchema)
              .build();

      Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
      log.info("OX 스트리밍 생성 시작: 목표={}문항", quizCount);

      // 스트리밍 파서: 문항 객체가 완성될 때마다 즉시 SSE 전달
      StreamingQuestionExtractor extractor =
          new StreamingQuestionExtractor(
              objectMapper,
              question -> {
                if (delivered.get() >= quizCount) return;
                if (question.selections() != null
                    && question.selections().size() > MAX_SELECTION_COUNT) return;

                int count = delivered.incrementAndGet();
                request
                    .questionsConsumer()
                    .accept(GeminiQuestionMapper.toDto(List.of(question), metadata.sourcePages()));

                long now = System.nanoTime();
                firstNanos.compareAndSet(0, now);
                lastNanos.updateAndGet(prev -> Math.max(prev, now));

                if (count == 1) {
                  long ttfqMs = (now - startNanos) / 1_000_000;
                  log.info("TTFQ (Time To First Question): {}ms", ttfqMs);
                }
              });

      // 스트리밍 실행
      Flux<org.springframework.ai.chat.model.ChatResponse> stream = chatModel.stream(prompt);
      stream
          .doOnNext(
              response -> {
                response.getResult();
                if (response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                  extractor.feed(response.getResult().getOutput().getText());
                }

                if (response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getCompletionTokens() > 0) {
                  var usage = response.getMetadata().getUsage();
                  long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                  double cost = metricsRecorder.recordChunkResult(elapsedMs, usage);
                  totalCost.add(cost);
                  long thinkingTokens =
                      usage instanceof GoogleGenAiUsage g && g.getThoughtsTokenCount() != null
                          ? g.getThoughtsTokenCount()
                          : 0;
                  log.info(
                      "Gemini Usage - streaming, 토큰: 입력={}, 추론={}, 출력={}, 비용=${}",
                      usage.getPromptTokens(),
                      thinkingTokens,
                      usage.getCompletionTokens(),
                      String.format("%.6f", cost));

                  // AI 비용 이벤트 발행 (사이드카) — 실패해도 생성 흐름에는 절대 영향 주지 않는다
                  try {
                    long cachedTokens =
                        usage instanceof GoogleGenAiUsage gc
                                && gc.getCachedContentTokenCount() != null
                            ? gc.getCachedContentTokenCount()
                            : 0;
                    long nonCachedInput = Math.max(0, usage.getPromptTokens() - cachedTokens);
                    aiCostRecorder.record(
                        new AiInvocationCommand(
                            requestId,
                            request.userId(),
                            request.quizSetId(),
                            configuredModel,
                            response.getMetadata().getModel(),
                            nonCachedInput,
                            cachedTokens,
                            thinkingTokens,
                            usage.getCompletionTokens(),
                            elapsedMs,
                            InvocationStatus.SUCCESS,
                            null,
                            Instant.now()));
                    costRecorded.set(true);
                  } catch (Exception costError) {
                    log.warn(
                        "[AI 비용 기록 실패] quizSetId={} — 생성 흐름에는 영향 없음",
                        request.quizSetId(),
                        costError);
                  }
                }
              })
          .blockLast(java.time.Duration.ofMinutes(6));

      log.info(
          "OX 스트리밍 생성 완료: 전달={}문항, 총 소요={}ms",
          delivered.get(),
          (System.nanoTime() - startNanos) / 1_000_000);

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(1, startNanos, first, last, totalCost.sum());
      return 1;

    } catch (IllegalStateException e) {
      // blockLast 타임아웃 — cause가 TimeoutException인 경우만 정상 처리
      if (!(e.getCause() instanceof java.util.concurrent.TimeoutException)) {
        recordFailure(requestId, request, costRecorded, "INFRA", startNanos);
        throw new GeminiInfraException("Gemini 블로킹 컨텍스트 오류", e);
      }
      recordFailure(requestId, request, costRecorded, "TIMEOUT", startNanos);
      log.warn("[OX 스트리밍 타임아웃] 6분 초과, 생성된 문항 유지 deliveredCount={}", delivered.get());
      metricsRecorder.recordStreamingTimeout("OX");
      metricsRecorder.recordRequestDuration(
          1,
          startNanos,
          firstNanos.get() == 0 ? null : firstNanos.get(),
          lastNanos.get() == 0 ? null : lastNanos.get(),
          totalCost.sum());
      return 1;
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      if (delivered.get() > 0) {
        recordFailure(requestId, request, costRecorded, "PARTIAL", startNanos);
        log.warn("[OX 부분 성공] 스트리밍 중 오류 발생이나 문항 전달됨 deliveredCount={}", delivered.get(), e);
        metricsRecorder.recordStreamingTimeout("OX");
        metricsRecorder.recordRequestDuration(
            1,
            startNanos,
            firstNanos.get() == 0 ? null : firstNanos.get(),
            lastNanos.get() == 0 ? null : lastNanos.get(),
            totalCost.sum());
        return 1;
      }
      recordFailure(requestId, request, costRecorded, "INFRA", startNanos);
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    }
  }

  /** AI 호출 실패를 원장/Outbox에 기록한다(토큰 0, status=ERROR). 이미 성공 적재됐으면 건너뛴다. B-1: 부분 토큰 정밀 집계는 추후 개선. */
  private void recordFailure(
      String requestId,
      GenerationRequestToAI request,
      AtomicBoolean costRecorded,
      String errorCode,
      long startNanos) {
    if (costRecorded.get()) return;
    try {
      aiCostRecorder.record(
          new AiInvocationCommand(
              requestId,
              request.userId(),
              request.quizSetId(),
              configuredModel,
              null,
              0L,
              0L,
              0L,
              0L,
              (System.nanoTime() - startNanos) / 1_000_000,
              InvocationStatus.ERROR,
              errorCode,
              Instant.now()));
      costRecorded.set(true);
    } catch (Exception costError) {
      log.warn("[AI 비용 실패기록 실패] quizSetId={} — 생성 흐름 영향 없음", request.quizSetId(), costError);
    }
  }
}
