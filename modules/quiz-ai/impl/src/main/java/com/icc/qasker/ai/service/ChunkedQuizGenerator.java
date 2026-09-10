package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizConsumer;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Selection;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.metric.GeminiMetricsRecorder;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.regeneration.prompt.RegenerationPrompt;
import com.icc.qasker.ai.support.ChunkPlanner;
import com.icc.qasker.ai.support.GeminiContextCacheManager;
import com.icc.qasker.ai.support.StreamingJsonArrayExtractor;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.quiz.QuizType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class ChunkedQuizGenerator {

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QAskerAiProperties aiProperties;
  private final QualityVerifier verifier;
  private final GeminiContextCacheManager cacheManager;

  public ChunkedQuizGenerator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties,
      QualityVerifier verifier) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.aiProperties = aiProperties;
    this.verifier = verifier;
    this.cacheManager = new GeminiContextCacheManager(chatModel, metricsRecorder);
  }

  public <T> void generate(GenerationRequestToAI request, QuizTypeSpec<T> spec) {
    new Session<>(request, spec).run();
  }

  private final class Session<T> {

    // 요청 파라미터
    private final GenerationRequestToAI request;
    private final QuizTypeSpec<T> spec;
    private final QuizConsumer consumer;
    private final int quizCount;
    private final QuizType quizType;
    private final String genGuideLine;

    // PDF (외부 자원 핸들)
    private final FileMetadata metadata;
    private final Media pdfMedia;

    // 컨텍스트 캐시 — ① 캐시 생성 단계가 채우고 finally 에서 해제한다
    private CacheRef genCache;
    private CacheRef verifyCache;

    // 비동기 검증 인프라
    private final ExecutorService verifyExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore verifySlots = new Semaphore(aiProperties.getConcurrency().getVerify());
    private final List<CompletableFuture<Void>> verifyFutures = new CopyOnWriteArrayList<>();

    // 대화 스레드 — 청크 루프(단일 스레드)만 쓴다
    private final List<Message> conversation = new ArrayList<>();

    // 진행 상태 — 검증·재생성 워커가 동시에 갱신한다
    private final Queue<HeldProblem> heldQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger delivered = new AtomicInteger(0);
    private final AtomicInteger submitted = new AtomicInteger(0);

    // 청크 진행 — ② 퀴즈 생성 단계(단일 스레드)만 쓴다
    private int chunkTotal;
    private int chunksDone;

    // 계측
    private final long startNanos = System.nanoTime();
    private final AtomicLong firstNanos = new AtomicLong(0);
    private final AtomicLong lastNanos = new AtomicLong(0);
    private final DoubleAdder totalCost = new DoubleAdder();

    private Session(GenerationRequestToAI request, QuizTypeSpec<T> spec) {
      this.request = request;
      this.spec = spec;
      this.consumer = request.consumer();
      this.quizCount = request.quizCount();
      this.quizType = spec.getSupportedType();
      this.genGuideLine = spec.systemGuideLine(request.language());
      this.metadata = resolvePdf(request);
      this.pdfMedia =
          new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));
    }

    private void run() {
      try {
        // 1. 캐시 생성
        createCaches();
        // 2. 청크 분할 — 요청 문항 수를 한 번에 요청할 크기로 나눈다
        List<Integer> chunkPlan = planChunks();
        // 3. 퀴즈 생성 — 청크마다 요청하고, 문항을 스트림으로 받으며 평가를 비동기로 제출한다
        generateQuizzes(chunkPlan);
        // 4. 평가 — 제출된 평가가 모두 정착할 때까지 기다린다
        awaitVerifications();
        // 5. 재생성 — 평가에서 탈락한 문항을 1문제씩 다시 만든다
        regenerateHeld();
      } catch (CustomException e) {
        // [③ 세션] 비즈니스 오류 — delivered 판정 전에 검증을 정착시킨다(finally 는 catch 뒤라 늦다)
        awaitVerifications();
        if (delivered.get() == 0) {
          throw e;
        }
        log.warn("{} 퀴즈 생성 중 비즈니스 오류. {}문항 보존.", quizType.name(), delivered.get(), e);
      } catch (Exception e) {
        // [③ 세션] 인프라 오류 — 한 문항도 못 냈을 때만 장애로 올린다
        awaitVerifications();
        if (delivered.get() == 0) {
          throw new GeminiInfraException("Gemini 인프라 장애", e);
        }
        log.warn("{} 퀴즈 생성 중 인프라 오류. {}문항 보존.", quizType.name(), delivered.get(), e);
        metricsRecorder.recordStreamingTimeout(quizType);
      } finally {
        awaitVerifications();
        verifyExecutor.shutdown();
        releaseCaches();
      }
      recordOutcome();
    }

    // ══════════════════════════════════════════════════════════════════
    //  1. 캐시 생성
    // ══════════════════════════════════════════════════════════════════
    private void createCaches() {
      // [④ 부수] 생성 실패는 예외가 아니라 빈 값 — null 로 두고 캐시 없는 폴백으로 강등한다
      this.genCache =
          cacheManager
              .create(quizType.name(), genGuideLine, metadata.uri(), aiProperties.getCacheTtl())
              .orElse(null);

      this.verifyCache = verifier.createPass1Cache(metadata.uri(), quizType).orElse(null);
    }

    private void releaseCaches() {
      cacheManager.delete(quizType.name(), genCache == null ? null : genCache.name());
      verifier.deletePass1Cache(verifyCache);
    }

    // ══════════════════════════════════════════════════════════════════
    //  2. 청크 분할
    // ══════════════════════════════════════════════════════════════════
    private List<Integer> planChunks() {
      List<Integer> chunkPlan =
          ChunkPlanner.plan(quizCount, aiProperties.getChunk().getChunkSize());
      chunkTotal = chunkPlan.size();
      log.info(
          "{} 문제 수에 맞게 청크 분할 완료: 요청={}문항, chunk-size={}, 청크={}개",
          quizType.name(),
          quizCount,
          aiProperties.getChunk().getChunkSize(),
          chunkTotal);
      return chunkPlan;
    }

    // ══════════════════════════════════════════════════════════════════
    //  3. 퀴즈 생성
    // ══════════════════════════════════════════════════════════════════
    private void generateQuizzes(List<Integer> chunkPlan) {
      for (int chunkIndex = 0; chunkIndex < chunkPlan.size(); chunkIndex++) {
        // 앞선 청크의 스트림이 이미 목표를 채웠으면 남은 청크는 요청하지 않는다
        if (submitted.get() >= quizCount) break;

        // 이번 청크의 사용자 메시지 — 둘째 청크부터 중복 방지 지시를 붙이고, PDF 는 첫 청크에만 싣는다(캐시가 없을 때)
        String userPrompt =
            spec.requestPrompt(chunkPlan.get(chunkIndex), request.customInstruction());
        if (chunkIndex > 0) {
          userPrompt = userPrompt + spec.dedupInstruction();
        }
        UserMessage.Builder requestBuilder = UserMessage.builder().text(userPrompt);
        if (genCache == null && chunkIndex == 0) {
          requestBuilder.media(pdfMedia);
        }
        UserMessage chunkRequest = requestBuilder.build();

        // 보낼 프롬프트 = 시스템 가이드(캐시가 없을 때) + 지금까지의 대화 + 이번 요청
        List<Message> messages = new ArrayList<>();
        if (genCache == null) {
          messages.add(new SystemMessage(genGuideLine));
        }
        messages.addAll(conversation);
        messages.add(chunkRequest);
        Prompt prompt =
            new Prompt(
                messages, buildOptions(spec.responseSchema(request.customInstruction()), genCache));

        // 추출기는 버퍼·중괄호 깊이를 들고 있어 스트림 하나당 하나여야 한다 — 조각마다 새로 만들면 문항이 끝내 완성되지 않는다
        StreamingJsonArrayExtractor<T> extractor =
            new StreamingJsonArrayExtractor<>(objectMapper, spec.elementType(), quizType.name());
        List<AIProblem> streamed = new ArrayList<>();

        String usageLabel = quizType.name() + " chunk #" + chunkIndex;
        long chunkStartNanos = System.nanoTime();
        chatModel.stream(prompt)
            .doOnNext(
                response -> {
                  String text = extractText(response);
                  if (text != null) {
                    // 이번 조각에서 완성된 문항들을 여기서 직접 처리한다 — 추출기는 파싱만 하고,
                    // 저장(DB)·SSE 전송도 이 모듈이 아니라 QuizConsumer 구현(quiz-make)이 한다
                    for (T question : extractor.feed(text)) {
                      try {
                        // submitted 는 줄지 않으므로 목표를 채웠으면 남은 문항도 볼 것 없다
                        if (submitted.get() >= quizCount) break;
                        if (!spec.accept(question)) continue;

                        AIProblem arranged = spec.toProblem(question, metadata.sourcePages());
                        int order = submitted.incrementAndGet();
                        streamed.add(arranged);
                        // 앞쪽 N문항은 검증을 건너뛰고 바로 넘긴다(TTFQ 단축) — 품질은 사후 Pass 2 가 본다
                        if (order <= aiProperties.getFastServeCount()) {
                          verifyFutures.add(submitFastServe(arranged));
                        } else {
                          verifyFutures.add(submitVerification(arranged));
                        }
                      } catch (Exception e) {
                        // [① 문항] 여기서 새면 Flux 에러가 되어 청크 전체가 죽는다
                        log.warn("{} 문항 처리 실패 — 문항 제외", quizType.name(), e);
                      }
                    }
                  }
                  recordUsage(response, usageLabel);
                })
            .blockLast();
        metricsRecorder.recordChunkCall(chunkIndex, chunkStartNanos);

        // 다음 청크가 앞 문항과 겹치지 않도록 이번 요청·응답을 대화 이력에 남긴다
        conversation.add(chunkRequest);
        conversation.add(new AssistantMessage(serializeProblems(streamed)));

        // [② 청크] 한 문항도 안 나왔다 — 이어서 요청해도 같을 가능성이 커 여기서 멈춘다
        if (streamed.isEmpty()) {
          log.warn("{} 청크 #{} 산출물 없음 — 부분 저장·완료.", quizType.name(), chunkIndex);
          break;
        }
        chunksDone++;
      }
    }

    // ══════════════════════════════════════════════════════════════════
    //  4. 평가
    // ══════════════════════════════════════════════════════════════════
    private CompletableFuture<Void> submitFastServe(AIProblem problem) {
      return CompletableFuture.runAsync(
          () -> {
            try {
              deliverToConsumer(problem);
            } catch (Exception e) {
              // [① 문항] 저장 실패 — 이 문항만 버린다
              log.warn("{} 즉석 서빙 저장 실패 — 문항 제외", quizType.name(), e);
            }
          },
          verifyExecutor);
    }

    private CompletableFuture<Void> submitVerification(AIProblem problem) {
      return CompletableFuture.runAsync(
          () -> {
            try {
              verifySlots.acquire();
              try {
                QualityVerdict verdict = verifier.verify(verificationRequest(problem));
                if (verdict.result() == QualityVerdict.Result.BELOW_THRESHOLD) {
                  log.info("{} 게이트 미달 보류 — 사유: {}", quizType.name(), verdict.feedback());
                  heldQueue.add(new HeldProblem(problem, verdict.feedback()));
                  return;
                }
                deliverToConsumer(problem);
              } finally {
                verifySlots.release();
              }
            } catch (InterruptedException e) {
              // 종료 신호 — 플래그만 복원하고 조용히 빠진다
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              // [① 문항] 검증기 오류 — 이 문항만 버린다(검증 불가 판정은 검증기가 자체 폴백)
              log.warn("{} 비동기 검증 실패 — 문항 제외", quizType.name(), e);
            }
          },
          verifyExecutor);
    }

    /** 이 문항을 Pass 1 검증 요청으로 옮긴다. verifyCache 가 있으면 검증관이 PDF 원문과 직접 대조한다. */
    private QualityVerificationRequest verificationRequest(AIProblem problem) {
      List<Selection> selections =
          problem.selections() == null
              ? List.of()
              : problem.selections().stream()
                  .map(s -> new Selection(s.content(), s.correct()))
                  .toList();
      return new QualityVerificationRequest(
          quizType,
          request.language(),
          problem.content(),
          selections,
          QualityVerificationRequest.modelAnswerOf(quizType, selections),
          request.customInstruction(),
          problem.appliedInstruction(),
          Mode.PASS_1,
          verifyCache);
    }

    private void deliverToConsumer(AIProblem problem) {
      int number = consumer.saveProblem(problem);
      consumer.recordV1(number, problem, null);
      int d = delivered.incrementAndGet();

      long now = System.nanoTime();
      firstNanos.compareAndSet(0, now);
      lastNanos.updateAndGet(prev -> Math.max(prev, now));
      if (d == 1) {
        log.info("TTFQ (Time To First Question): {}ms", (now - startNanos) / 1_000_000);
      }
    }

    private void awaitVerifications() {
      try {
        CompletableFuture.allOf(verifyFutures.toArray(CompletableFuture[]::new)).join();
      } catch (Exception e) {
        // [① 문항] 개별 검증이 예외로 끝났을 뿐 — 나머지 결과는 이미 정착했으므로 진행한다
        log.warn("{} 검증 대기 중 오류", quizType.name(), e);
      }
    }

    // ══════════════════════════════════════════════════════════════════
    //  5. 재생성 (1문제씩)
    // ══════════════════════════════════════════════════════════════════
    private void regenerateHeld() {
      if (heldQueue.isEmpty()) {
        return;
      }
      int workers = Math.min(aiProperties.getConcurrency().getRegenerate(), heldQueue.size());
      log.info("{} 보류 문항 재생성 시작: {}건 (워커 {})", quizType.name(), heldQueue.size(), workers);

      List<CompletableFuture<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        tasks.add(CompletableFuture.runAsync(this::drainHeldQueue, verifyExecutor));
      }
      CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }

    private void drainHeldQueue() {
      HeldProblem held;
      while ((held = heldQueue.poll()) != null) {
        try {
          AIProblem v2 = regenerateOne(held);
          if (v2 != null) {
            // v2는 검증 없이 저장한다
            int number = consumer.saveProblem(v2);
            consumer.recordV1(number, held.problem(), held.feedback());
            consumer.recordV2(number, v2);
            delivered.incrementAndGet();
          }
        } catch (Exception e) {
          // [① 문항] 재생성 불가 → 제외(문항 수가 목표보다 줄어든다)
          log.warn("{} 보류 문항 재생성 실패 — 제외", quizType.name(), e);
        }
      }
    }

    private AIProblem regenerateOne(HeldProblem held) {
      List<Message> messages = new ArrayList<>();
      if (genCache == null) {
        messages.add(new SystemMessage(genGuideLine));
      }
      messages.addAll(conversation);
      messages.add(new UserMessage(buildRegenerationPrompt(held)));

      String schema = spec.responseSchema(request.customInstruction());
      ChatResponse response = chatModel.call(new Prompt(messages, buildOptions(schema, genCache)));
      recordUsage(response, quizType.name() + " regenerate");

      // [① 문항] 셋 중 어디서 걸리든 null — 호출부가 "재생성 불가"로 보고 이 문항을 제외한다
      String text = extractText(response);
      if (text == null) {
        return null;
      }
      Optional<T> first = spec.parseFirst(text);
      if (first.isEmpty()) {
        return null;
      }
      T q = first.get();
      if (!spec.accept(q)) {
        return null;
      }
      return spec.toProblem(q, metadata.sourcePages());
    }

    // ══════════════════════════════════════════════════════════════════
    //  계측 (단계 아님 — 각 단계가 호출)
    // ══════════════════════════════════════════════════════════════════
    private void recordUsage(ChatResponse response, String label) {
      if (response.getMetadata().getUsage().getCompletionTokens() > 0) {
        double cost =
            metricsRecorder.recordChunkUsage(label, startNanos, response.getMetadata().getUsage());
        totalCost.add(cost);
      }
    }

    private void recordOutcome() {
      log.info(
          "{} 청크 생성 완료: 전달={}문항(목표 {}), 배치 완료={}/{}, 총 소요={}ms",
          quizType.name(),
          delivered.get(),
          quizCount,
          chunksDone,
          chunkTotal,
          (System.nanoTime() - startNanos) / 1_000_000);

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(chunksDone, startNanos, first, last, totalCost.sum());
      metricsRecorder.recordQuizCounts(quizType, quizCount, delivered.get(), chunksDone);
    }
  }

  private FileMetadata resolvePdf(GenerationRequestToAI request) {
    String cacheKey =
        geminiFileService.generateCacheKey(request.fileUrl(), request.referencePages());
    try {
      return geminiFileService
          .awaitCachedFileMetadata(cacheKey)
          .orElseGet(
              () -> geminiFileService.uploadPdf(request.fileUrl(), request.referencePages()));
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiInfraException("PDF 업로드 실패", e);
    }
  }

  private static String extractText(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return null;
    }
    return response.getResult().getOutput().getText();
  }

  private GoogleGenAiChatOptions buildOptions(String responseSchema, CacheRef cacheRef) {
    GoogleGenAiChatOptions.Builder builder =
        GoogleGenAiChatOptions.builder()
            .responseMimeType("application/json")
            .responseSchema(responseSchema);
    if (cacheRef != null) {
      // 추론 요청 모델은 반드시 캐시 생성 모델과 일치해야 한다(불일치 시 Vertex 400).
      builder.model(cacheRef.model()).useCachedContent(true).cachedContentName(cacheRef.name());
    }
    return builder.build();
  }

  private record HeldProblem(AIProblem problem, String feedback) {}

  private String buildRegenerationPrompt(HeldProblem held) {
    String problemJson;
    try {
      problemJson = objectMapper.writeValueAsString(held.problem());
    } catch (Exception e) {
      // [④ 부수] 프롬프트에 실을 표현일 뿐 — toString 으로 대체하고 재생성은 계속한다
      problemJson = String.valueOf(held.problem());
    }
    return RegenerationPrompt.forHeldProblem(problemJson, held.feedback());
  }

  private String serializeProblems(List<AIProblem> problems) {
    try {
      return objectMapper.writeValueAsString(Map.of("questions", problems));
    } catch (Exception e) {
      // [④ 부수] 대화 이력용이라 비워도 생성은 계속된다(중복 방지 품질만 떨어짐)
      log.warn("배치 문항 직렬화 실패. 빈 히스토리로 대체.", e);
      return "{\"questions\":[]}";
    }
  }
}
