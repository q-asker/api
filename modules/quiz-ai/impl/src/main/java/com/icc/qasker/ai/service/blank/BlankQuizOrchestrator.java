package com.icc.qasker.ai.service.blank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.prompt.strategy.QuizType;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingQuestionExtractor;
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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

/**
 * 빈칸채우기(BLANK) 퀴즈 오케스트레이터. 캐시 없이 1회 호출 + 응답 스트리밍으로 문항이 완성될 때마다 즉시 SSE 전달한다.
 *
 * <p>단일 LLM 호출이므로 청크 간 중복이 원천적으로 불가능하고, 캐시 생성/삭제 오버헤드도 없다.
 */
@Slf4j
@Component
public class BlankQuizOrchestrator implements QuizTypeOrchestrator {

  private static final int MAX_SELECTION_COUNT = 4;
  private static final String RESPONSE_JSON_SCHEMA =
      new BeanOutputConverter<>(com.icc.qasker.ai.structure.GeminiResponse.class).getJsonSchema();

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;

  public BlankQuizOrchestrator(
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
    return "BLANK";
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
      // GCS에 PDF 업로드 (캐시 없이 GCS URI 직접 사용)
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(request.fileUrl())
              .orElseGet(() -> geminiFileService.uploadPdf(request.fileUrl()));

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

      var options =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(RESPONSE_JSON_SCHEMA)
              .build();

      Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
      log.info("BLANK 스트리밍 생성 시작 (캐시 없음): 목표={}문항", quizCount);

      // 스트리밍 파서: 문항 객체가 완성될 때마다 즉시 SSE 전달
      StreamingQuestionExtractor extractor =
          new StreamingQuestionExtractor(
              objectMapper,
              question -> {
                if (delivered.get() >= quizCount) return;
                if (question.selections() != null
                    && question.selections().size() > MAX_SELECTION_COUNT) return;

                int count = delivered.incrementAndGet();
                request.questionsConsumer().accept(GeminiQuestionMapper.toDto(List.of(question)));

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
          "BLANK 스트리밍 생성 완료: 전달={}문항, 총 소요={}ms",
          delivered.get(),
          (System.nanoTime() - startNanos) / 1_000_000);

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(1, startNanos, first, last, totalCost.sum());
      return 1;

    } catch (IllegalStateException e) {
      // blockLast 타임아웃 — cause가 TimeoutException인 경우만 정상 처리
      if (!(e.getCause() instanceof java.util.concurrent.TimeoutException)) {
        throw new GeminiInfraException("Gemini 블로킹 컨텍스트 오류", e);
      }
      log.warn("BLANK 스트리밍 타임아웃 (6분 초과): 생성된 문항 {}개 유지", delivered.get());
      metricsRecorder.recordStreamingTimeout("BLANK");
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
        log.warn("BLANK 스트리밍 중 오류 발생이나 {}개 문항은 전달됨. 부분 성공 처리.", delivered.get(), e);
        metricsRecorder.recordStreamingTimeout("BLANK");
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
