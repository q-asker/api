package com.icc.qasker.ai.service.essay;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.RegenerationRecord;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiEssayQuestionMapper;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingJsonArrayExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiEssayQuestion;
import com.icc.qasker.ai.structure.GeminiEssayResponse;
import com.icc.qasker.ai.structure.GeminiEssayResponseSchema;
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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/** 서술형(ESSAY) 퀴즈 오케스트레이터. 캐시 없이 1회 호출 + 응답 스트리밍으로 문항이 완성될 때마다 즉시 SSE 전달한다. */
@Slf4j
@Component
public class EssayQuizOrchestrator implements QuizTypeOrchestrator {

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QualityGate qualityGate;

  public EssayQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QualityGate qualityGate) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.qualityGate = qualityGate;
  }

  /** 생성 게이트에서 미달 판정돼 보류된 ESSAY 문항(v1)과 개선 피드백. */
  private record EssayHeld(AIProblem problem, String feedback) {}

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

      // 생성 게이트에서 미달 판정된 문항의 보류 큐. 스트리밍 종료 후 1회 재생성으로 소진한다.
      List<EssayHeld> heldQueue = new ArrayList<>();

      // 스트리밍 파서: 문항 객체가 완성될 때마다 게이트 검증 후 통과분만 즉시 SSE 전달
      StreamingJsonArrayExtractor<GeminiEssayQuestion> extractor =
          new StreamingJsonArrayExtractor<>(
              objectMapper,
              GeminiEssayQuestion.class,
              question -> {
                if (delivered.get() >= quizCount) return;

                List<AIProblem> problems =
                    GeminiEssayQuestionMapper.toDto(List.of(question), metadata.sourcePages())
                        .quiz();
                for (AIProblem p : problems) {
                  if (delivered.get() >= quizCount) return;

                  QualityVerdict verdict =
                      qualityGate.verify(
                          p, "ESSAY", request.language(), request.customInstruction());
                  if (verdict.result() == QualityVerdict.Result.BELOW_THRESHOLD) {
                    heldQueue.add(new EssayHeld(p, verdict.feedback()));
                    continue;
                  }

                  request.sink().saveProblem(p.withQuality(QualityGate.toStatus(verdict), null));
                  int count = delivered.incrementAndGet();

                  long now = System.nanoTime();
                  firstNanos.compareAndSet(0, now);
                  lastNanos.updateAndGet(prev -> Math.max(prev, now));

                  if (count == 1) {
                    long ttfqMs = (now - startNanos) / 1_000_000;
                    log.info("TTFQ (Time To First Question): {}ms", ttfqMs);
                  }
                }
              },
              "ESSAY");

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
                  double cost =
                      metricsRecorder.recordChunkUsage(
                          "streaming", startNanos, response.getMetadata().getUsage());
                  totalCost.add(cost);
                }
              })
          .blockLast(java.time.Duration.ofMinutes(6));

      // 보류된 미달 문항을 1회 재생성해 통과분을 채운다(원문 PDF 재첨부).
      processEssayHeldQueue(
          heldQueue,
          systemMessage,
          pdfMedia,
          responseSchema,
          metadata,
          request,
          quizCount,
          delivered,
          startNanos,
          totalCost);

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

  /** 보류된 ESSAY 미달 문항을 세션 내에서 1회씩 재생성한다(1회 캡). 통과→OK, v2 미달→BELOW_THRESHOLD+feedback, 재생성 불가→제외. */
  private void processEssayHeldQueue(
      List<EssayHeld> heldQueue,
      SystemMessage systemMessage,
      Media pdfMedia,
      String responseSchema,
      FileMetadata metadata,
      GenerationRequestToAI request,
      int quizCount,
      AtomicInteger delivered,
      long startNanos,
      DoubleAdder totalCost) {
    if (heldQueue.isEmpty()) {
      return;
    }
    log.info("ESSAY 보류 문항 재생성 시작: {}건", heldQueue.size());
    for (EssayHeld held : heldQueue) {
      if (delivered.get() >= quizCount) {
        break;
      }
      try {
        AIProblem v2 =
            regenerateEssay(
                held, systemMessage, pdfMedia, responseSchema, metadata, startNanos, totalCost);
        if (v2 == null) {
          metricsRecorder.recordRegenerationOutcome("UNABLE");
          continue;
        }
        QualityVerdict verdict =
            qualityGate.verify(v2, "ESSAY", request.language(), request.customInstruction());
        String status;
        String feedback;
        if (verdict.passed()) {
          status = "OK";
          feedback = null;
          metricsRecorder.recordRegenerationOutcome("PASS");
        } else {
          status = "BELOW_THRESHOLD";
          feedback = verdict.feedback();
          metricsRecorder.recordRegenerationOutcome("BELOW_THRESHOLD");
        }
        AIProblem marked = v2.withQuality(status, feedback);
        int number = request.sink().saveProblem(marked);
        delivered.incrementAndGet();
        request
            .sink()
            .logRegeneration(
                new RegenerationRecord(number, toJson(held.problem()), held.feedback()));
      } catch (Exception e) {
        metricsRecorder.recordRegenerationOutcome("UNABLE");
        log.warn("ESSAY 보류 문항 재생성 실패 — 제외", e);
      }
    }
  }

  private AIProblem regenerateEssay(
      EssayHeld held,
      SystemMessage systemMessage,
      Media pdfMedia,
      String responseSchema,
      FileMetadata metadata,
      long startNanos,
      DoubleAdder totalCost) {
    UserMessage user =
        UserMessage.builder().text(buildEssayRegenerationPrompt(held)).media(pdfMedia).build();
    var options =
        GoogleGenAiChatOptions.builder()
            .responseMimeType("application/json")
            .responseSchema(responseSchema)
            .build();
    ChatResponse response = chatModel.call(new Prompt(List.of(systemMessage, user), options));
    if (response.getMetadata() != null
        && response.getMetadata().getUsage() != null
        && response.getMetadata().getUsage().getCompletionTokens() > 0) {
      totalCost.add(
          metricsRecorder.recordChunkUsage(
              "ESSAY regenerate", startNanos, response.getMetadata().getUsage()));
    }
    String text =
        response.getResult() == null || response.getResult().getOutput() == null
            ? null
            : response.getResult().getOutput().getText();
    if (text == null) {
      return null;
    }
    GeminiEssayResponse parsed = new BeanOutputConverter<>(GeminiEssayResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return null;
    }
    return GeminiEssayQuestionMapper.toDto(
            List.of(parsed.questions().getFirst()), metadata.sourcePages())
        .quiz()
        .stream()
        .findFirst()
        .orElse(null);
  }

  /** 단건 문항을 JSON으로 직렬화한다(재생성 비교 로그용). 실패 시 null. */
  private String toJson(AIProblem problem) {
    try {
      return objectMapper.writeValueAsString(problem);
    } catch (Exception e) {
      log.warn("재생성 로그용 ESSAY 문항 직렬화 실패", e);
      return null;
    }
  }

  private String buildEssayRegenerationPrompt(EssayHeld held) {
    String json;
    try {
      json = objectMapper.writeValueAsString(held.problem());
    } catch (Exception e) {
      json = String.valueOf(held.problem());
    }
    return "직전 요청으로 생성한 아래 서술형 문항이 품질 검증에서 미달 판정됐다. 첨부된 원문을 근거로 지적된 문제를 해결한 개선 문항 1개만 생성한다."
        + " questions 배열에 개선 문항 1개만 담아 응답한다.\n\n# 미달 문항\n"
        + json
        + "\n\n# 미달 사유·개선 지침\n"
        + (held.feedback() == null ? "(사유 미상)" : held.feedback());
  }
}
