package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiCacheService.CacheInfo;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.support.GeminiChatService;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.QuizPlannerService;
import com.icc.qasker.ai.service.support.QuizPlannerService.PlanResult;
import com.icc.qasker.ai.service.support.QuizPlannerService.QuizPlanItem;
import com.icc.qasker.ai.service.support.SelectionEqualizer;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiQuestion.GeminiSelection;
import com.icc.qasker.ai.util.ChunkSplitter;
import com.icc.qasker.global.error.CustomException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

  private static final int MAX_SELECTION_COUNT = 4;

  private final QAskerAiProperties.Chunk chunkProperties;
  private final GeminiFileService geminiFileService;
  private final GeminiCacheService geminiCacheService;
  private final GeminiChatService geminiChatService;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QuizPlannerService quizPlannerService;
  private final SelectionEqualizer selectionEqualizer;

  public QuizOrchestrationServiceImpl(
      QAskerAiProperties aiProperties,
      GeminiFileService geminiFileService,
      GeminiCacheService geminiCacheService,
      GeminiChatService geminiChatService,
      GeminiMetricsRecorder metricsRecorder,
      QuizPlannerService quizPlannerService,
      SelectionEqualizer selectionEqualizer) {
    this.chunkProperties = aiProperties.getChunk();
    this.geminiFileService = geminiFileService;
    this.geminiCacheService = geminiCacheService;
    this.geminiChatService = geminiChatService;
    this.metricsRecorder = metricsRecorder;
    this.quizPlannerService = quizPlannerService;
    this.selectionEqualizer = selectionEqualizer;
  }

  // ════════════════════════════════════════════════════════════════
  // 오케스트레이션
  // ════════════════════════════════════════════════════════════════

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long requestStartNanos = System.nanoTime();
    AtomicLong firstQuizNanos = new AtomicLong(0);
    AtomicLong lastQuizNanos = new AtomicLong(0);
    DoubleAdder totalCostAdder = new DoubleAdder();

    int maxChunkCount = 0;
    CacheInfo cacheInfo = null;
    try {
      // 준비: 파일 캐시 + 시스템 프롬프트 캐싱
      FileMetadata metadata =
          geminiFileService
              .awaitCachedFileMetadata(request.fileUrl())
              .orElseGet(() -> geminiFileService.uploadPdf(request.fileUrl()));

      cacheInfo =
          geminiCacheService.createCache(
              metadata.uri(), request.strategyValue(), request.language());

      maxChunkCount = chunkProperties.pickMaxCount();

      List<ChunkInfo> chunks =
          ChunkSplitter.createPageChunks(
              request.referencePages(), request.quizCount(), maxChunkCount);
      log.info("청크 분할 완료: {}개 청크 (maxChunkCount={})", chunks.size(), maxChunkCount);

      // 청크별 병렬 파이프라인 (fast chunk 전략)
      // 첫 번째 청크: plan 없이 즉시 생성 → 빠른 첫 응답
      // 나머지 청크: plan 완료 후 병렬 생성 → 높은 품질
      AtomicInteger remainingQuota = new AtomicInteger(request.quizCount());
      final String finalCacheName = cacheInfo.name();
      boolean isMultiple = "MULTIPLE".equals(request.strategyValue());

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());

        // Fast chunks: 첫 2개 청크 즉시 생성 (plan 없이)
        int fastCount = Math.min(2, chunks.size());
        for (int i = 0; i < fastCount; i++) {
          final ChunkInfo fastChunk = chunks.get(i);
          futures.add(
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      log.info(
                          "Fast chunk 즉시 생성: pages={}, {}문제",
                          fastChunk.referencedPages(),
                          fastChunk.quizCount());
                      new ChunkProcessor(fastChunk, finalCacheName, request, null)
                          .generate()
                          .sanitize()
                          .equalize()
                          .deliver(remainingQuota, totalCostAdder, firstQuizNanos, lastQuizNanos);
                    } catch (Exception e) {
                      log.error(
                          "Fast chunk 처리 실패: pages={}, error={}",
                          fastChunk.referencedPages(),
                          e.getMessage());
                    }
                  },
                  executor));
        }

        // 나머지 청크: plan 후 병렬 생성
        if (chunks.size() > fastCount) {
          List<ChunkInfo> remainingChunks = chunks.subList(fastCount, chunks.size());
          futures.add(
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      // plan 실행 (나머지 청크만)
                      List<String> planExtras;
                      if (isMultiple) {
                        PlanResult planResult =
                            quizPlannerService.plan(
                                remainingChunks, finalCacheName, request.language());
                        planExtras = buildChunkPlanExtras(remainingChunks, planResult);
                        if (planResult != null) {
                          metricsRecorder.recordPlan(
                              planResult.inputTokens(),
                              planResult.outputTokens(),
                              planResult.cost());
                          totalCostAdder.add(planResult.cost());
                        }
                      } else {
                        planExtras = remainingChunks.stream().map(c -> (String) null).toList();
                      }

                      // 나머지 청크 병렬 생성
                      List<CompletableFuture<Void>> chunkFutures =
                          new ArrayList<>(remainingChunks.size());
                      for (int i = 0; i < remainingChunks.size(); i++) {
                        final ChunkInfo chunk = remainingChunks.get(i);
                        final String planExtra = planExtras.get(i);
                        chunkFutures.add(
                            CompletableFuture.runAsync(
                                () -> {
                                  try {
                                    new ChunkProcessor(chunk, finalCacheName, request, planExtra)
                                        .generate()
                                        .equalize()
                                        .deliver(
                                            remainingQuota,
                                            totalCostAdder,
                                            firstQuizNanos,
                                            lastQuizNanos);
                                  } catch (Exception e) {
                                    log.error(
                                        "청크 처리 실패 (계속 진행): pages={}, error={}",
                                        chunk.referencedPages(),
                                        e.getMessage());
                                  }
                                },
                                executor));
                      }
                      CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new))
                          .join();
                    } catch (Exception e) {
                      log.error("Plan+생성 파이프 실패: {}", e.getMessage());
                    }
                  },
                  executor));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      log.info("전체 병렬 생성 완료");

      Long firstNanos = firstQuizNanos.get() == 0 ? null : firstQuizNanos.get();
      Long lastNanos = lastQuizNanos.get() == 0 ? null : lastQuizNanos.get();
      metricsRecorder.recordRequestDuration(
          maxChunkCount, requestStartNanos, firstNanos, lastNanos, totalCostAdder.sum());

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiInfraException("Gemini 인프라 장애", e);
    } finally {
      if (cacheInfo != null) {
        geminiCacheService.deleteCache(cacheInfo.name());
      }
    }
    return maxChunkCount;
  }

  // ════════════════════════════════════════════════════════════════
  // ChunkProcessor — Fluent Interface로 청크별 파이프라인 실행
  // ════════════════════════════════════════════════════════════════

  /** 단일 청크의 3단계 파이프라인을 Fluent Interface로 실행한다. 각 단계가 실패하면 이후 단계를 자동 스킵한다. */
  private class ChunkProcessor {

    private final ChunkInfo chunk;
    private final String cacheName;
    private final GenerationRequestToAI request;
    private final String planExtra;

    private List<GeminiQuestion> questions;
    private double totalCost = 0.0;
    private boolean stopped = false;

    ChunkProcessor(
        ChunkInfo chunk, String cacheName, GenerationRequestToAI request, String planExtra) {
      this.chunk = chunk;
      this.cacheName = cacheName;
      this.request = request;
      this.planExtra = planExtra;
    }

    /** Step 2: 문제 생성 + 검증 */
    ChunkProcessor generate() throws Exception {
      GeminiChatService.ParsedResult parsed =
          geminiChatService.callAndParse(
              chunk, cacheName, request.strategyValue(), request.language(), planExtra);

      if (parsed == null
          || parsed.response() == null
          || CollectionUtils.isEmpty(parsed.response().questions())) {
        stopped = true;
        return this;
      }

      questions =
          parsed.response().questions().stream()
              .filter(q -> q.selections() == null || q.selections().size() <= MAX_SELECTION_COUNT)
              .toList();

      if (questions.isEmpty()) {
        log.warn("유효한 문제가 존재하지 않습니다: pages={}", chunk.referencedPages());
        stopped = true;
        return this;
      }

      totalCost += parsed.cost();
      return this;
    }

    /** Step 2.5: selections 번호 prefix 제거 + content 내 선택지 중복 제거 */
    ChunkProcessor sanitize() {
      if (stopped) return this;

      List<GeminiQuestion> result = new ArrayList<>(questions.size());
      for (GeminiQuestion q : questions) {
        // 1. selections에서 "숫자. " prefix 제거
        List<GeminiSelection> cleanedSels = new ArrayList<>();
        for (GeminiSelection sel : q.selections()) {
          String c = sel.content();
          if (c != null) {
            c = c.replaceFirst("^\\d+\\.\\s*", "");
          }
          cleanedSels.add(new GeminiSelection(c, sel.correct(), sel.explanation()));
        }

        // 2. content에서 selections과 중복되는 번호 목록 제거
        String content = q.content();
        if (content != null && !cleanedSels.isEmpty()) {
          String firstSel = cleanedSels.get(0).content();
          if (firstSel != null && content.contains(firstSel)) {
            for (GeminiSelection sel : cleanedSels) {
              if (sel.content() != null) {
                content = content.replace(sel.content(), "");
              }
            }
            content = content.replaceAll("\\n\\s*\\d+\\.\\s*", "\n");
            content = content.replaceAll("\\n{3,}", "\n\n").strip();
            log.info("content 선택지 중복 제거: {}자 → {}자", q.content().length(), content.length());
          }
        }

        result.add(
            new GeminiQuestion(content, cleanedSels, q.quizExplanation(), q.referencedPages()));
      }
      questions = result;
      return this;
    }

    /** Step 3: 선택지 균등화 (MULTIPLE만) */
    ChunkProcessor equalize() {
      if (stopped) return this;
      if (!"MULTIPLE".equals(request.strategyValue())) return this;

      metricsRecorder.incrementSelectionChecked(questions.size());

      List<GeminiQuestion> result = new ArrayList<>(questions);
      int equalizedCount = 0;

      for (int i = 0; i < result.size(); i++) {
        GeminiQuestion q = result.get(i);
        if (!isCorrectLongest(q)) continue;

        // 정답 인덱스 찾기
        int correctIdx = -1;
        for (int j = 0; j < q.selections().size(); j++) {
          if (q.selections().get(j).correct()) {
            correctIdx = j;
            break;
          }
        }
        if (correctIdx == -1) continue;

        List<String> allContents = q.selections().stream().map(GeminiSelection::content).toList();
        SelectionEqualizer.EqualizeResult eqResult =
            selectionEqualizer.equalize(allContents, correctIdx, request.language());
        if (eqResult == null) continue;

        metricsRecorder.recordEqualization(
            eqResult.inputTokens(), eqResult.outputTokens(), eqResult.cost());
        totalCost += eqResult.cost();
        equalizedCount++;

        List<GeminiSelection> newSels = new ArrayList<>();
        for (int j = 0; j < q.selections().size(); j++) {
          GeminiSelection orig = q.selections().get(j);
          newSels.add(
              new GeminiSelection(eqResult.contents().get(j), orig.correct(), orig.explanation()));
        }
        result.set(
            i, new GeminiQuestion(q.content(), newSels, q.quizExplanation(), q.referencedPages()));
      }

      if (equalizedCount > 0) {
        log.info("선택지 균등화 {}건 완료, 균등화 비용: ${}", equalizedCount, String.format("%.6f", totalCost));
      }
      questions = result;
      return this;
    }

    /** 할당량 확보 + 소비자 전달 + 비용/시각 기록 */
    void deliver(
        AtomicInteger remainingQuota,
        DoubleAdder costAdder,
        AtomicLong firstNanos,
        AtomicLong lastNanos) {
      if (stopped) return;

      costAdder.add(totalCost);

      int size = questions.size();
      int before = remainingQuota.getAndUpdate(r -> Math.max(0, r - size));
      int claimed = Math.min(size, before);

      if (claimed == 0) {
        log.info(
            "이미 요청 수({})만큼 생성 완료, 초과분 버림: pages={}", request.quizCount(), chunk.referencedPages());
        return;
      }

      List<GeminiQuestion> toDeliver = questions;
      if (claimed < size) {
        log.info("초과 문제 {}개 제거: pages={}", size - claimed, chunk.referencedPages());
        toDeliver = questions.subList(0, claimed);
      }

      AIProblemSet result = GeminiQuestionMapper.toDto(toDeliver);
      request.questionsConsumer().accept(result);

      long now = System.nanoTime();
      firstNanos.compareAndSet(0, now);
      lastNanos.updateAndGet(prev -> Math.max(prev, now));
    }
  }

  // ════════════════════════════════════════════════════════════════
  // Step 1: 문항 계획
  // ════════════════════════════════════════════════════════════════

  /** 1회 API 호출로 전체 문항의 마크다운 서식을 결정한다. 계획 실패 시 모든 항목이 null인 리스트를 반환한다. */
  private List<String> buildChunkPlanExtras(List<ChunkInfo> chunks, PlanResult planResult) {
    List<String> extras = new ArrayList<>(chunks.size());

    if (planResult == null || planResult.items() == null) {
      log.info("문항 계획 실패 — 기존 파이프라인으로 폴백");
      for (int i = 0; i < chunks.size(); i++) extras.add(null);
      return extras;
    }

    int offset = 0;
    for (ChunkInfo chunk : chunks) {
      int end = Math.min(offset + chunk.quizCount(), planResult.items().size());
      List<QuizPlanItem> chunkItems = planResult.items().subList(offset, end);
      extras.add(formatPlanExtra(chunkItems));
      offset = end;
    }

    log.info("문항 계획 완료: {}개 문항 서식 결정", planResult.items().size());
    return extras;
  }

  private String formatPlanExtra(List<QuizPlanItem> items) {
    if (items.isEmpty()) return null;
    StringBuilder sb = new StringBuilder("\n[서식 지시]");
    for (int i = 0; i < items.size(); i++) {
      QuizPlanItem item = items.get(i);
      sb.append("\n").append(i + 1).append("번 문항:");
      if (item.format() != null && !"none".equalsIgnoreCase(item.format())) {
        sb.append(" [").append(item.format()).append("]");
      }
      if (item.formatUsage() != null && !item.formatUsage().isBlank()) {
        sb.append(" ").append(item.formatUsage());
      }
    }
    return sb.toString();
  }

  // ════════════════════════════════════════════════════════════════
  // 유틸리티
  // ════════════════════════════════════════════════════════════════

  private boolean isCorrectLongest(GeminiQuestion question) {
    if (question.selections() == null || question.selections().size() < 2) return false;
    int correctLength = 0;
    int maxWrongLength = 0;
    for (GeminiSelection sel : question.selections()) {
      int len = sel.content() != null ? sel.content().length() : 0;
      if (sel.correct()) {
        correctLength = len;
      } else {
        maxWrongLength = Math.max(maxWrongLength, len);
      }
    }
    return correctLength > maxWrongLength && maxWrongLength > 0;
  }
}
