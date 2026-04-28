package com.icc.qasker.ai.service.essay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiEssayQuestionMapper;
import com.icc.qasker.ai.prompt.strategy.QuizType;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingEssayQuestionExtractor;
import com.icc.qasker.ai.structure.GeminiEssayResponseSchema;
import com.icc.qasker.global.error.CustomException;
import java.net.URI;
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
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

/** 서술형(ESSAY) 퀴즈 오케스트레이터. 캐시 없이 1회 호출 + 응답 스트리밍으로 문항이 완성될 때마다 즉시 SSE 전달한다. */
@Slf4j
@Component
public class EssayQuizOrchestrator implements QuizTypeOrchestrator {

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;

  public EssayQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public String getSupportedType() {
    return "ESSAY";
  }

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long startNanos = System.nanoTime();

    DoubleAdder totalCost = new DoubleAdder();
    AtomicLong firstNanos = new AtomicLong(0);
    AtomicLong lastNanos = new AtomicLong(0);
    AtomicInteger delivered = new AtomicInteger(0);
    int quizCount = request.quizCount();

    try {
      // PDF 업로드
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

      String responseSchema = GeminiEssayResponseSchema.forInstruction(request.customInstruction());
      var options =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(responseSchema)
              .build();

      Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
      log.info("ESSAY 스트리밍 생성 시작: 목표={}문항", quizCount);

      // 스트리밍 파서: 문항 객체가 완성될 때마다 즉시 SSE 전달
      StreamingEssayQuestionExtractor extractor =
          new StreamingEssayQuestionExtractor(
              objectMapper,
              question -> {
                if (delivered.get() >= quizCount) return;

                int count = delivered.incrementAndGet();
                request
                    .questionsConsumer()
                    .accept(
                        GeminiEssayQuestionMapper.toDto(List.of(question), metadata.sourcePages()));

                long now = System.nanoTime();
                firstNanos.compareAndSet(0, now);
                lastNanos.updateAndGet(prev -> Math.max(prev, now));

                if (count == 1) {
                  long ttfqMs = (now - startNanos) / 1_000_000;
                  log.info("TTFQ (Time To First Question): {}ms", ttfqMs);
                }
              });

      // 스트리밍 실행
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
                }
              })
          .blockLast(java.time.Duration.ofMinutes(6));

      log.info(
          "ESSAY 스트리밍 생성 완료: 전달={}문항, 총 소요={}ms",
          delivered.get(),
          (System.nanoTime() - startNanos) / 1_000_000);

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(1, startNanos, first, last, totalCost.sum());
      return 1;

    } catch (IllegalStateException e) {
      if (!(e.getCause() instanceof java.util.concurrent.TimeoutException)) {
        throw new GeminiInfraException("Gemini 블로킹 컨텍스트 오류", e);
      }
      log.warn("[ESSAY 스트리밍 타임아웃] 6분 초과, 생성된 문항 유지 deliveredCount={}", delivered.get());
      metricsRecorder.recordStreamingTimeout("ESSAY");
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
        log.warn("[ESSAY 부분 성공] 스트리밍 중 오류 발생이나 문항 전달됨 deliveredCount={}", delivered.get(), e);
        metricsRecorder.recordStreamingTimeout("ESSAY");
        metricsRecorder.recordRequestDuration(
            1,
            startNanos,
            firstNanos.get() == 0 ? null : firstNanos.get(),
            lastNanos.get() == 0 ? null : lastNanos.get(),
            totalCost.sum());
        return 1;
      }
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    }
  }
}
