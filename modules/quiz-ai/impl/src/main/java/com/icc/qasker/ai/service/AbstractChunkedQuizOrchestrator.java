package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.prompt.RegenerationPrompt;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.ChunkPlanner;
import com.icc.qasker.ai.service.support.GeminiContextCacheManager;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingJsonArrayExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.global.error.CustomException;
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
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public abstract class AbstractChunkedQuizOrchestrator<T> implements QuizTypeOrchestrator {

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QAskerAiProperties aiProperties;
  private final QualityGate qualityGate;
  private final GeminiContextCacheManager cacheManager;

  protected AbstractChunkedQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties,
      QualityGate qualityGate) {
    this.geminiFileService = geminiFileService;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.aiProperties = aiProperties;
    this.qualityGate = qualityGate;
    this.cacheManager = new GeminiContextCacheManager(chatModel, metricsRecorder);
  }

  protected abstract String dedupInstruction();

  protected abstract Class<T> elementType();

  protected abstract String responseSchema(String customInstruction);

  protected abstract AIProblem toProblem(T question, List<Integer> sourcePages);

  protected abstract boolean accept(T question);

  protected abstract Optional<T> parseFirst(String text);

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    new Session(request).run();
  }

  private final class Session {

    // 요청 파라미터
    private final GenerationRequestToAI request;
    private final QuizBatchSink sink;
    private final int quizCount;
    private final QuizType quizType;
    private final String tag = getSupportedType().toString();
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

    private Session(GenerationRequestToAI request) {
      this.request = request;
      this.sink = request.sink();
      this.quizCount = request.quizCount();
      this.quizType = getSupportedType();
      this.genGuideLine = quizType.getSystemGuideLine(request.language());
      this.metadata = resolvePdf(request);
      this.pdfMedia =
          new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));
    }

    private void run() {
      try {
        // 1. 캐시 생성
        createCaches();
        // 2. 퀴즈 생성 — 문항을 스트림으로 받으며 평가를 비동기로 제출한다
        generateChunks();
        // 3. 평가 — 제출된 평가가 모두 정착할 때까지 기다린다
        awaitVerifications();
        // 4. 재생성 — 평가에서 탈락한 문항을 1문제씩 다시 만든다
        regenerateHeld();
      } catch (CustomException e) {
        awaitVerifications();
        if (delivered.get() == 0) throw e;
        log.warn("{} 퀴즈 생성 중 비즈니스 오류. {}문항 보존.", tag, delivered.get(), e);
      } catch (Exception e) {
        awaitVerifications();
        if (delivered.get() == 0) throw new GeminiInfraException("Gemini 인프라 장애", e);
        log.warn("{} 퀴즈 생성 중 인프라 오류. {}문항 보존.", tag, delivered.get(), e);
        metricsRecorder.recordStreamingTimeout(tag);
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
      this.genCache =
          cacheManager
              .create(tag, genGuideLine, metadata.uri(), aiProperties.getCacheTtl())
              .orElse(null);
      this.verifyCache = qualityGate.createPass1Cache(metadata.uri(), quizType.name()).orElse(null);
    }

    private void releaseCaches() {
      cacheManager.delete(tag, genCache == null ? null : genCache.name());
      qualityGate.deletePass1Cache(verifyCache);
    }

    // ══════════════════════════════════════════════════════════════════
    //  2. 퀴즈 생성
    // ══════════════════════════════════════════════════════════════════
    private void generateChunks() {
      List<Integer> chunkPlan =
          ChunkPlanner.plan(quizCount, aiProperties.getChunk().getChunkSize());
      chunkTotal = chunkPlan.size();
      log.info(
          "{} 문제 수에 맞게 청크 분할 완료: 요청={}문항, chunk-size={}, 청크={}개",
          tag,
          quizCount,
          aiProperties.getChunk().getChunkSize(),
          chunkTotal);

      for (int chunkIndex = 0; chunkIndex < chunkTotal; chunkIndex++) {
        if (submitted.get() >= quizCount) break;

        List<AIProblem> generated = generateChunk(chunkIndex, chunkPlan.get(chunkIndex));

        if (generated.isEmpty()) {
          log.warn("{} 청크 #{} 산출물 없음 — 부분 저장·완료.", tag, chunkIndex);
          break;
        }
        chunksDone++;
      }
    }

    private List<AIProblem> generateChunk(int chunkIndex, int chunkSize) {
      String userPrompt =
          quizType.generateRequestPrompt(
              request.referencePages(), chunkSize, request.customInstruction());

      if (chunkIndex > 0) {
        userPrompt = userPrompt + dedupInstruction();
      }

      UserMessage.Builder ub = UserMessage.builder().text(userPrompt);
      if (genCache == null && chunkIndex == 0) {
        ub.media(pdfMedia);
      }
      UserMessage phase1User = ub.build();

      List<Message> messages = new ArrayList<>();
      if (genCache == null) {
        messages.add(new SystemMessage(genGuideLine));
      }
      messages.addAll(conversation);
      messages.add(phase1User);
      String schema = responseSchema(request.customInstruction());
      Prompt prompt = new Prompt(messages, buildOptions(schema, genCache));
      List<AIProblem> generated = new ArrayList<>();
      StreamingJsonArrayExtractor<T> extractor =
          new StreamingJsonArrayExtractor<>(
              objectMapper,
              elementType(),
              question -> {
                if (submitted.get() >= quizCount) return;
                if (!accept(question)) return;

                AIProblem arranged = toProblem(question, metadata.sourcePages());
                int order = submitted.incrementAndGet();
                generated.add(arranged);
                if (order <= aiProperties.getFastServeCount()) {
                  verifyFutures.add(submitFastServe(arranged));
                } else {
                  verifyFutures.add(submitVerification(arranged));
                }
              },
              tag);

      streamInto(prompt, extractor, chunkIndex);

      conversation.add(phase1User);
      conversation.add(new AssistantMessage(serializeProblems(generated)));
      return generated;
    }

    private void streamInto(
        Prompt prompt, StreamingJsonArrayExtractor<T> extractor, int chunkIndex) {
      Flux<ChatResponse> stream = chatModel.stream(prompt);
      stream
          .doOnNext(
              response -> {
                String text = extractText(response);
                if (text != null) extractor.feed(text);
                recordUsage(response, tag + " chunk #" + chunkIndex);
              })
          .blockLast();
    }

    // ══════════════════════════════════════════════════════════════════
    //  3. 평가
    // ══════════════════════════════════════════════════════════════════
    private CompletableFuture<Void> submitFastServe(AIProblem problem) {
      return CompletableFuture.runAsync(
          () -> {
            try {
              store(problem, null);
            } catch (Exception e) {
              log.warn("{} 즉석 서빙 저장 실패 — 문항 제외", tag, e);
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
                QualityVerdict verdict =
                    qualityGate.verify(
                        problem,
                        quizType.name(),
                        request.language(),
                        request.customInstruction(),
                        verifyCache);
                if (verdict.result() == QualityVerdict.Result.BELOW_THRESHOLD) {
                  log.info("{} 게이트 미달 보류 — 사유: {}", tag, verdict.feedback());
                  heldQueue.add(new HeldProblem(problem, verdict.feedback()));
                  return;
                }
                store(problem, null);
              } finally {
                verifySlots.release();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              log.warn("{} 비동기 검증 실패 — 문항 제외", tag, e);
            }
          },
          verifyExecutor);
    }

    private void store(AIProblem problem, String v1Feedback) {
      int number = sink.saveProblem(problem);
      sink.recordV1(number, problem, v1Feedback);
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
        log.warn("{} 검증 대기 중 오류", tag, e);
      }
    }

    // ══════════════════════════════════════════════════════════════════
    //  4. 재생성 (1문제씩)
    // ══════════════════════════════════════════════════════════════════
    private void regenerateHeld() {
      if (heldQueue.isEmpty()) {
        return;
      }
      int workers = Math.min(aiProperties.getConcurrency().getRegenerate(), heldQueue.size());
      log.info("{} 보류 문항 재생성 시작: {}건 (워커 {})", tag, heldQueue.size(), workers);

      // 목표 문항 수까지 남은 칸을 티켓으로 나눠 갖는다. 워커가 호출 전에 하나 집고 산출 실패 시 반납해
      // 다음 항목이 쓰게 한다 — 동시에 돌아도 목표 문항 수를 초과 저장하지 않는다.
      Semaphore budget = new Semaphore(Math.max(0, quizCount - delivered.get()));

      List<CompletableFuture<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        tasks.add(CompletableFuture.runAsync(() -> drainHeldQueue(budget), verifyExecutor));
      }
      CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }

    private void drainHeldQueue(Semaphore budget) {
      while (budget.tryAcquire()) {
        HeldProblem held = heldQueue.poll();
        if (held == null) {
          budget.release();
          return;
        }
        boolean produced = false;
        try {
          AIProblem v2 = regenerateOne(held);
          if (v2 != null) {
            // v2는 검증 없이 저장한다
            int number = sink.saveProblem(v2);
            sink.recordV1(number, held.problem(), held.feedback());
            sink.recordV2(number, v2);
            delivered.incrementAndGet();
            produced = true;
          }
        } catch (Exception e) {
          // 재생성 불가 → 제외(문항 수 축소).
          log.warn("{} 보류 문항 재생성 실패 — 제외", tag, e);
        }
        if (!produced) {
          budget.release();
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

      String schema = responseSchema(request.customInstruction());
      ChatResponse response = chatModel.call(new Prompt(messages, buildOptions(schema, genCache)));
      recordUsage(response, tag + " regenerate");

      String text = extractText(response);
      if (text == null) {
        return null;
      }
      Optional<T> first = parseFirst(text);
      if (first.isEmpty()) {
        return null;
      }
      T q = first.get();
      if (!accept(q)) {
        return null;
      }
      return toProblem(q, metadata.sourcePages());
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
          tag,
          delivered.get(),
          quizCount,
          chunksDone,
          chunkTotal,
          (System.nanoTime() - startNanos) / 1_000_000);

      Long first = firstNanos.get() == 0 ? null : firstNanos.get();
      Long last = lastNanos.get() == 0 ? null : lastNanos.get();
      metricsRecorder.recordRequestDuration(chunksDone, startNanos, first, last, totalCost.sum());
      metricsRecorder.recordQuizCounts(tag, quizCount, delivered.get(), chunksDone);
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
    } else {
      response.getResult();
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
      problemJson = String.valueOf(held.problem());
    }
    return RegenerationPrompt.forHeldProblem(problemJson, held.feedback());
  }

  private String serializeProblems(List<AIProblem> problems) {
    try {
      return objectMapper.writeValueAsString(Map.of("questions", problems));
    } catch (Exception e) {
      log.warn("배치 문항 직렬화 실패. 빈 히스토리로 대체.", e);
      return "{\"questions\":[]}";
    }
  }
}
