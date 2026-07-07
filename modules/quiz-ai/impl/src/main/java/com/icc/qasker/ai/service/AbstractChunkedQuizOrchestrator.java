package com.icc.qasker.ai.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIExplanation;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.RegenerationRecord;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingJsonArrayExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiExplanationResponse;
import com.icc.qasker.ai.structure.GeminiExplanationResponse.GeminiExplanation;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import com.icc.qasker.global.error.CustomException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * 청크형(MULTIPLE/BLANK/OX) 퀴즈 오케스트레이터의 공통 골격 — 배치 인터리빙 2단계 생성(Q9).
 *
 * <p>chunk-size 단위 배치마다 <b>Phase 1(문제)</b> → <b>Phase 2(해설)</b>를 교차 호출한다. 모든 호출은 하나의 멀티턴 대화 스레드에
 * 누적되어(전체 누적) 배치 간 중복을 방지한다. Phase 1(문제)·Phase 2(해설)는 <b>분리된 시스템 프롬프트</b>를 쓰며, 공통인 문서(PDF) 프리픽스만
 * <b>명시적 Vertex 컨텍스트 캐시</b>로 만들어 인터리빙 호출 간 재사용하고 생성 종료 시 삭제한다(재과금 절감). 캐시 생성 불가 시(비지원 ChatModel·최소
 * 토큰 미달 등) PDF를 매 호출 전송하는 폴백으로 자동 강등한다.
 *
 * <ul>
 *   <li>Phase 1: 해설을 제외한 스키마로 문제·선지만 생성 → {@link QuizBatchSink#saveProblem}로 즉시 저장·통지(풀이 가능). 선지는
 *       {@link #arrangeSelections}로 최종 정렬한 뒤 저장하므로, 저장 순서가 대화 히스토리(어시스턴트 턴)와 일치한다.
 *   <li>Phase 2: 직전 배치 문항의 선지별 해설을 <b>스트리밍</b> 생성하고 완성될 때마다 {@link QuizBatchSink#saveExplanation}으로
 *       문항 1건씩 저장(단일 엔티티 read-modify-write). 실패 시 1회 재시도, 그래도 실패하면 해당 배치 해설은 비운 채(해설 없음) 다음 배치로
 *       진행한다(문제 우선).
 * </ul>
 *
 * <p>Phase 1 배치가 산출물 0건으로 실패하면 직전까지 저장한 배치만 남기고 루프를 종료한다(부분 저장·완료).
 */
@Slf4j
public abstract class AbstractChunkedQuizOrchestrator implements QuizTypeOrchestrator {

  /** Phase 2 해설 생성 실패 시 재시도 횟수(FR-007). */
  private static final int PHASE2_MAX_RETRY = 1;

  /** 컨텍스트 캐시 TTL — 한 세트 생성 세션(인터리빙 전체)을 커버하고, 종료 시 명시적으로 삭제한다. */
  private static final Duration CACHE_TTL = Duration.ofMinutes(15);

  private final GeminiFileService geminiFileService;
  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QAskerAiProperties aiProperties;
  private final QualityGate qualityGate;

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
  }

  /** 문항 최대 선택지 수. 초과 문항은 drop 된다 (MULTIPLE/BLANK=4, OX=2). */
  protected abstract int maxSelectionCount();

  /** 청크 K(K≥2) 사용자 프롬프트 꼬리에 붙는 타입별 중복 회피 지침 문구. */
  protected abstract String dedupInstruction();

  /**
   * 선지를 최종 저장 순서로 정렬한다(MULTIPLE/BLANK 셔플, OX O-우선 정규화). 저장 순서 = 대화 히스토리 순서 = Phase 2 해설 정렬 기준이 되므로
   * 이 시점에 확정한다.
   */
  protected abstract List<AISelection> arrangeSelections(List<AISelection> selections);

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    long startNanos = System.nanoTime();
    String tag = getSupportedType();

    DoubleAdder totalCost = new DoubleAdder();
    AtomicLong firstNanos = new AtomicLong(0);
    AtomicLong lastNanos = new AtomicLong(0);
    AtomicInteger delivered = new AtomicInteger(0);
    int quizCount = request.quizCount();
    QuizBatchSink sink = request.sink();

    QuizType quizType = QuizType.valueOf(request.strategyValue());
    // 프롬프트 분리: Phase 1(문제)·Phase 2(해설)가 서로 다른 시스템 프롬프트를 쓴다.
    String problemGuideLine = quizType.getProblemGuideLine(request.language());
    String explanationGuideLine = quizType.getExplanationGuideLine(request.language());

    FileMetadata metadata = resolvePdf(request);
    Media pdfMedia =
        new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));

    List<Integer> chunkPlan = aiProperties.getChunk().planChunks(quizCount);
    log.info(
        "{} 인터리빙 분할: 요청={}문항, chunk-size={}, 청크={}개",
        tag,
        quizCount,
        aiProperties.getChunk().getChunkSize(),
        chunkPlan.size());

    // 컨텍스트 캐시: phase별 시스템 프롬프트+PDF를 각각 캐시(Vertex는 캐시 사용 시 요청 systemInstruction 금지). 실패 시 null → 폴백.
    CacheRef problemCache = tryCreateCache(problemGuideLine, metadata);
    CacheRef explanationCache = tryCreateCache(explanationGuideLine, metadata);

    // 멀티턴 대화 스레드 — U/A 턴만 누적. 캐시 사용 시 system·PDF는 캐시에 있고, 폴백 시 각 요청이 phase별 system(+PDF)을 붙인다.
    List<Message> conversation = new ArrayList<>();

    // 생성 게이트에서 미달 판정된 문항의 보류 큐(세션 스코프). 청크 루프 종료 후 1회 재생성으로 소진한다.
    List<HeldProblem> heldQueue = new ArrayList<>();

    int chunksDone = 0;
    try {
      for (int chunkIndex = 0; chunkIndex < chunkPlan.size(); chunkIndex++) {
        if (delivered.get() >= quizCount) break;
        int chunkSize = chunkPlan.get(chunkIndex);

        List<BatchEntry> batch =
            runProblemPhase(
                chunkIndex,
                chunkSize,
                quizType,
                problemGuideLine,
                request,
                metadata,
                pdfMedia,
                problemCache,
                conversation,
                quizCount,
                delivered,
                sink,
                startNanos,
                firstNanos,
                lastNanos,
                totalCost,
                heldQueue);

        if (batch.isEmpty()) {
          // Phase 1 배치 산출물 0건 → 부분 저장·완료(재시도 없음, FR-007)
          log.warn("{} 청크 #{} Phase 1 산출물 없음 — 부분 저장·완료.", tag, chunkIndex);
          break;
        }
        chunksDone++;

        if (delivered.get() >= quizCount) {
          sink.markProblemsReady();
        }

        runExplanationPhase(
            chunkIndex,
            batch,
            explanationGuideLine,
            explanationCache,
            conversation,
            sink,
            tag,
            startNanos,
            totalCost);
      }

      // 보류된 미달 문항을 세션 내에서 1회 재생성해 통과분을 채운다(원문 컨텍스트·대화 스레드 활용).
      processHeldQueue(
          heldQueue,
          conversation,
          problemCache,
          explanationCache,
          explanationGuideLine,
          metadata,
          quizType,
          request,
          sink,
          quizCount,
          delivered,
          startNanos,
          totalCost);
    } catch (CustomException e) {
      if (delivered.get() == 0) throw e;
      log.warn("{} 인터리빙 도중 비즈니스 오류. {}문항 보존.", tag, delivered.get(), e);
    } catch (Exception e) {
      if (delivered.get() == 0) throw new GeminiInfraException("Gemini 인프라 장애", e);
      log.warn("{} 인터리빙 도중 인프라 오류. {}문항 보존.", tag, delivered.get(), e);
      metricsRecorder.recordStreamingTimeout(tag);
    } finally {
      deleteCache(problemCache);
      deleteCache(explanationCache);
    }

    sink.markProblemsReady();

    log.info(
        "{} 인터리빙 완료: 전달={}문항(목표 {}), 배치 완료={}/{}, 총 소요={}ms",
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

  /** PDF 업로드(캐시) 후 FileMetadata를 확보한다. */
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

  /** Phase 1: 문제 배치를 스트리밍 생성·저장하고, 저장된 (번호, 정렬된 문항) 목록을 반환한다. */
  private List<BatchEntry> runProblemPhase(
      int chunkIndex,
      int chunkSize,
      QuizType quizType,
      String problemGuideLine,
      GenerationRequestToAI request,
      FileMetadata metadata,
      Media pdfMedia,
      CacheRef cacheRef,
      List<Message> conversation,
      int quizCount,
      AtomicInteger delivered,
      QuizBatchSink sink,
      long startNanos,
      AtomicLong firstNanos,
      AtomicLong lastNanos,
      DoubleAdder totalCost,
      List<HeldProblem> heldQueue) {

    String tag = getSupportedType();
    String userPrompt =
        quizType.generateRequestPrompt(
            request.referencePages(), chunkSize, request.customInstruction());
    if (chunkIndex > 0) {
      userPrompt = userPrompt + dedupInstruction();
    }

    // 캐시가 있으면 PDF는 캐시 프리픽스에 있으므로 첨부하지 않는다. 폴백(캐시 없음) 시 첫 사용자 턴에만 첨부한다.
    UserMessage.Builder ub = UserMessage.builder().text(userPrompt);
    if (cacheRef == null && chunkIndex == 0) {
      ub.media(pdfMedia);
    }
    UserMessage phase1User = ub.build();

    // 캐시 사용 시 system·PDF는 캐시에 있으므로 요청엔 대화 턴만. 폴백 시 phase별 system(문제 전용)을 prepend.
    List<Message> messages = new ArrayList<>();
    if (cacheRef == null) {
      messages.add(new SystemMessage(problemGuideLine));
    }
    messages.addAll(conversation);
    messages.add(phase1User);

    String schema = GeminiResponseSchema.forProblemPhase(request.customInstruction());
    Prompt prompt = new Prompt(messages, buildOptions(schema, cacheRef));

    List<BatchEntry> batch = new ArrayList<>();
    StreamingJsonArrayExtractor<GeminiQuestion> extractor =
        new StreamingJsonArrayExtractor<>(
            objectMapper,
            GeminiQuestion.class,
            question -> {
              if (delivered.get() >= quizCount) return;
              if (question.selections() != null
                  && question.selections().size() > maxSelectionCount()) return;

              AIProblem arranged = toArrangedProblem(question, metadata.sourcePages());

              // 생성 게이트: sink.saveProblem 직전 검증. 미달(v1)은 보류(미저장·미노출)→재생성 큐, 통과·검증불가는 상태 부여 후 저장.
              QualityVerdict verdict =
                  qualityGate.verify(
                      arranged, quizType.name(), request.language(), request.customInstruction());
              if (verdict.result() == QualityVerdict.Result.BELOW_THRESHOLD) {
                log.info("{} 게이트 미달 보류 — 사유: {}", tag, verdict.feedback());
                heldQueue.add(new HeldProblem(arranged, verdict.feedback()));
                return;
              }

              AIProblem marked = arranged.withQuality(QualityGate.toStatus(verdict), null);
              int number = sink.saveProblem(marked);
              delivered.incrementAndGet();
              batch.add(new BatchEntry(number, marked));

              long now = System.nanoTime();
              firstNanos.compareAndSet(0, now);
              lastNanos.updateAndGet(prev -> Math.max(prev, now));
              if (delivered.get() == 1) {
                log.info("TTFQ (Time To First Question): {}ms", (now - startNanos) / 1_000_000);
              }
            },
            tag);

    streamInto(prompt, extractor, tag, chunkIndex, startNanos, totalCost);

    // 어시스턴트 턴 = 저장 순서로 정렬된 배치 문항 (대화 히스토리 정합)
    conversation.add(phase1User);
    conversation.add(new AssistantMessage(serializeProblems(batch)));
    return batch;
  }

  /**
   * Phase 2: 배치 문항의 선지별 해설을 스트리밍 생성한다. 해설 1건이 완성될 때마다 {@link QuizBatchSink#saveExplanation}로 즉시
   * 저장(단일 문항 read-modify-write). 실패 시 1회 재시도(이미 저장된 건은 재시도 시 동일 내용으로 덮어씀), 그래도 실패하면 해설 없이 진행.
   */
  private void runExplanationPhase(
      int chunkIndex,
      List<BatchEntry> batch,
      String explanationGuideLine,
      CacheRef cacheRef,
      List<Message> conversation,
      QuizBatchSink sink,
      String tag,
      long startNanos,
      DoubleAdder totalCost) {

    UserMessage phase2User = UserMessage.builder().text(buildExplanationPrompt(batch)).build();
    // 캐시 사용 시 system·PDF는 캐시에 있으므로 요청엔 대화 턴만. 폴백 시 phase별 system(해설 전용)을 prepend.
    List<Message> messages = new ArrayList<>();
    if (cacheRef == null) {
      messages.add(new SystemMessage(explanationGuideLine));
    }
    messages.addAll(conversation);
    messages.add(phase2User);
    Prompt prompt =
        new Prompt(messages, buildOptions(GeminiExplanationResponse.schema(), cacheRef));

    Set<Integer> batchNumbers = batch.stream().map(BatchEntry::number).collect(Collectors.toSet());

    for (int attempt = 0; attempt <= PHASE2_MAX_RETRY; attempt++) {
      StringBuilder fullText = new StringBuilder();
      StreamingJsonArrayExtractor<GeminiExplanation> extractor =
          new StreamingJsonArrayExtractor<>(
              objectMapper,
              GeminiExplanation.class,
              explanation -> {
                if (!batchNumbers.contains(explanation.number())) return;
                sink.saveExplanation(
                    new AIExplanation(explanation.number(), explanation.selectionExplanations()));
              },
              tag + " explanation #" + chunkIndex);
      try {
        Flux<ChatResponse> stream = chatModel.stream(prompt);
        stream
            .doOnNext(
                response -> {
                  String text = extractText(response);
                  if (text != null) {
                    fullText.append(text);
                    extractor.feed(text);
                  }
                  recordUsage(response, tag + " explanation #" + chunkIndex, startNanos, totalCost);
                })
            .blockLast(Duration.ofMinutes(6));
        conversation.add(phase2User);
        conversation.add(new AssistantMessage(fullText.toString()));
        return;
      } catch (Exception e) {
        if (attempt < PHASE2_MAX_RETRY) {
          log.warn(
              "{} 청크 #{} 해설 생성 실패, 재시도 {}/{}.", tag, chunkIndex, attempt + 1, PHASE2_MAX_RETRY, e);
        } else {
          log.warn("{} 청크 #{} 해설 생성 최종 실패 — 해당 배치 해설 없음으로 진행.", tag, chunkIndex, e);
        }
      }
    }
  }

  private void streamInto(
      Prompt prompt,
      StreamingJsonArrayExtractor<GeminiQuestion> extractor,
      String tag,
      int chunkIndex,
      long startNanos,
      DoubleAdder totalCost) {
    Flux<ChatResponse> stream = chatModel.stream(prompt);
    stream
        .doOnNext(
            response -> {
              String text = extractText(response);
              if (text != null) extractor.feed(text);
              recordUsage(response, tag + " chunk #" + chunkIndex, startNanos, totalCost);
            })
        // 대기 상한은 OkHttp 시한(connect/read/call, gemini-http 설정)이 전담한다 — 동기 풀 구조라
        // blockLast의 Duration은 발화 불능이므로 두지 않는다.
        .blockLast();
  }

  private void recordUsage(
      ChatResponse response, String label, long startNanos, DoubleAdder totalCost) {
    if (response.getMetadata() != null
        && response.getMetadata().getUsage() != null
        && response.getMetadata().getUsage().getCompletionTokens() > 0) {
      double cost =
          metricsRecorder.recordChunkUsage(label, startNanos, response.getMetadata().getUsage());
      totalCost.add(cost);
    }
  }

  private static String extractText(ChatResponse response) {
    if (response == null
        || response.getResult() == null
        || response.getResult().getOutput() == null) {
      return null;
    }
    return response.getResult().getOutput().getText();
  }

  /**
   * JSON 응답 옵션을 만든다. cacheName이 있으면 해당 컨텍스트 캐시를 참조한다({@code useCachedContent}는 {@code
   * cachedContentName}과 함께여야 ChatModel이 실제로 적용한다 — Spring AI 2.0.0의 {@code autoCache*}는 미소비 휴면 옵션).
   */
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

  /**
   * 시스템 프롬프트+PDF 프리픽스로 Vertex 컨텍스트 캐시를 생성한다. ChatModel이 GoogleGenAiChatModel이 아니거나 생성이 실패하면(최소 토큰
   * 미달 등) null을 반환해 캐시 없는 폴백으로 강등한다.
   */
  private CacheRef tryCreateCache(String systemPrompt, FileMetadata metadata) {
    if (!(chatModel instanceof GoogleGenAiChatModel genAiModel)) {
      return null;
    }
    try {
      String model = genAiModel.getOptions() == null ? null : genAiModel.getOptions().getModel();
      if (model == null || model.isBlank()) {
        return null;
      }
      // 시스템 프롬프트+PDF를 캐시에 넣는다. Vertex는 캐시 사용 시 요청에 systemInstruction을 금지하므로
      // phase별 시스템 프롬프트를 각각의 캐시에 담아 요청에는 대화 턴만 보낸다.
      Content pdf = Content.fromParts(Part.fromUri(metadata.uri(), "application/pdf"));
      CachedContentRequest request =
          CachedContentRequest.builder()
              .model(model)
              .systemInstruction(systemPrompt)
              .addContent(pdf)
              .ttl(CACHE_TTL)
              .build();
      GoogleGenAiCachedContent created = genAiModel.getCachedContentService().create(request);
      log.info("{} 컨텍스트 캐시 생성: name={}, model={}", getSupportedType(), created.getName(), model);
      return new CacheRef(created.getName(), model);
    } catch (Exception e) {
      log.warn("{} 컨텍스트 캐시 생성 실패 — 캐시 없이 진행(프리픽스 매 호출 전송).", getSupportedType(), e);
      return null;
    }
  }

  /** 컨텍스트 캐시를 삭제한다. 실패해도 TTL로 만료되므로 경고만 남긴다. */
  private void deleteCache(CacheRef cacheRef) {
    if (cacheRef == null || !(chatModel instanceof GoogleGenAiChatModel genAiModel)) {
      return;
    }
    try {
      genAiModel.getCachedContentService().delete(cacheRef.name());
      log.info("{} 컨텍스트 캐시 삭제: {}", getSupportedType(), cacheRef.name());
    } catch (Exception e) {
      log.warn("{} 컨텍스트 캐시 삭제 실패(TTL 만료 대기): {}", getSupportedType(), cacheRef.name(), e);
    }
  }

  /** 생성된 컨텍스트 캐시 참조(리소스 이름 + 생성 모델). 추론 요청은 동일 모델을 써야 한다. */
  private record CacheRef(String name, String model) {}

  /** GeminiQuestion → 선지 정렬된 AIProblem. */
  private AIProblem toArrangedProblem(GeminiQuestion question, List<Integer> sourcePages) {
    AIProblem mapped = GeminiQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
    List<AISelection> arranged =
        mapped.selections() == null ? List.of() : arrangeSelections(mapped.selections());
    return new AIProblem(
        mapped.content(),
        mapped.bloomsLevel(),
        arranged,
        mapped.referencedPages(),
        mapped.appliedInstruction(),
        mapped.rationale(),
        mapped.qualityMark());
  }

  /** 생성 게이트에서 미달 판정돼 보류된 문항(v1)과 개선 피드백. */
  private record HeldProblem(AIProblem problem, String feedback) {}

  /**
   * 보류된 미달 문항을 세션 내에서 1회씩 재생성한다(FR: 1회 캡). 통과→OK 저장, v2도 미달→BELOW_THRESHOLD+feedback 저장(노출), 재생성
   * 불가→제외. 각 결과를 메트릭으로 기록(SC-001 개선율). 이미 목표 문항 수를 채웠으면 중단한다.
   */
  private void processHeldQueue(
      List<HeldProblem> heldQueue,
      List<Message> conversation,
      CacheRef problemCache,
      CacheRef explanationCache,
      String explanationGuideLine,
      FileMetadata metadata,
      QuizType quizType,
      GenerationRequestToAI request,
      QuizBatchSink sink,
      int quizCount,
      AtomicInteger delivered,
      long startNanos,
      DoubleAdder totalCost) {
    if (heldQueue.isEmpty()) {
      return;
    }
    log.info("{} 보류 문항 재생성 시작: {}건", getSupportedType(), heldQueue.size());
    List<BatchEntry> regenBatch = new ArrayList<>();
    for (HeldProblem held : heldQueue) {
      if (delivered.get() >= quizCount) {
        break;
      }
      try {
        AIProblem v2 =
            regenerateOne(
                held,
                conversation,
                problemCache,
                metadata,
                quizType,
                request,
                startNanos,
                totalCost);
        if (v2 == null) {
          metricsRecorder.recordRegenerationOutcome("UNABLE");
          continue;
        }
        QualityVerdict verdict =
            qualityGate.verify(
                v2, quizType.name(), request.language(), request.customInstruction());
        String status;
        String feedback;
        if (verdict.passed()) {
          status = "OK";
          feedback = null;
          metricsRecorder.recordRegenerationOutcome("PASS");
        } else {
          // v2도 미달 → 제외하지 않고 노출하되 상태·피드백 표시(1회 캡).
          status = "BELOW_THRESHOLD";
          feedback = verdict.feedback();
          metricsRecorder.recordRegenerationOutcome("BELOW_THRESHOLD");
        }
        AIProblem marked = v2.withQuality(status, feedback);
        int number = sink.saveProblem(marked);
        delivered.incrementAndGet();
        regenBatch.add(new BatchEntry(number, marked));
        // 재생성 전후(v1 미달본 ↔ v2) 비교 로그. 학습자 세트엔 v2만 저장되고 이 기록은 분석 전용이다.
        // 재생성 원본(v1)을 품질 로그의 이 문항 행에 부착(전후 비교). v2는 problem+품질로그에 이미 저장됨.
        sink.logRegeneration(
            new RegenerationRecord(number, toJson(held.problem()), held.feedback()));
      } catch (Exception e) {
        // 재생성 불가 → 제외(문항 수 축소).
        metricsRecorder.recordRegenerationOutcome("UNABLE");
        log.warn("{} 보류 문항 재생성 실패 — 제외", getSupportedType(), e);
      }
    }

    // 재생성 문항도 Phase 2 해설을 생성한다(청크 루프 밖에서 저장돼 해설 단계를 건너뛰지 않도록).
    // 대화 히스토리에 재생성 문항을 어시스턴트 턴으로 얹어, 해설 프롬프트의 "직전 턴 문항" 참조가 성립하게 한다.
    if (!regenBatch.isEmpty()) {
      conversation.add(new UserMessage("아래는 품질 개선을 위해 재생성한 문항들이다."));
      conversation.add(new AssistantMessage(serializeProblems(regenBatch)));
      runExplanationPhase(
          0,
          regenBatch,
          explanationGuideLine,
          explanationCache,
          conversation,
          sink,
          getSupportedType(),
          startNanos,
          totalCost);
    }
  }

  /** 보류 문항 1건을 재생성한다. 살아있는 대화 스레드(원문 컨텍스트)에 개선 지시를 얹어 개선 문항 1개를 산출한다. */
  private AIProblem regenerateOne(
      HeldProblem held,
      List<Message> conversation,
      CacheRef cacheRef,
      FileMetadata metadata,
      QuizType quizType,
      GenerationRequestToAI request,
      long startNanos,
      DoubleAdder totalCost) {
    List<Message> messages = new ArrayList<>();
    if (cacheRef == null) {
      messages.add(new SystemMessage(quizType.getProblemGuideLine(request.language())));
    }
    messages.addAll(conversation);
    messages.add(new UserMessage(buildRegenerationPrompt(held)));

    String schema = GeminiResponseSchema.forProblemPhase(request.customInstruction());
    ChatResponse response = chatModel.call(new Prompt(messages, buildOptions(schema, cacheRef)));
    recordUsage(response, getSupportedType() + " regenerate", startNanos, totalCost);

    String text = extractText(response);
    if (text == null) {
      return null;
    }
    GeminiResponse parsed = new BeanOutputConverter<>(GeminiResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return null;
    }
    GeminiQuestion q = parsed.questions().getFirst();
    if (q.selections() != null && q.selections().size() > maxSelectionCount()) {
      return null;
    }
    return toArrangedProblem(q, metadata.sourcePages());
  }

  private String buildRegenerationPrompt(HeldProblem held) {
    String problemJson;
    try {
      problemJson = objectMapper.writeValueAsString(held.problem());
    } catch (Exception e) {
      problemJson = String.valueOf(held.problem());
    }
    return "직전에 생성한 아래 문항이 품질 검증에서 미달 판정됐다. 원문(직전 대화의 문서 근거)을 활용해 지적된 문제를 해결한 개선 문항 1개만 생성한다."
        + " 문항 유형·JSON 스키마는 동일하게 유지하고, questions 배열에 개선 문항 1개만 담아 응답한다.\n\n"
        + "# 미달 문항\n"
        + problemJson
        + "\n\n# 미달 사유·개선 지침\n"
        + (held.feedback() == null ? "(사유 미상)" : held.feedback());
  }

  /** 저장된 배치 문항을 어시스턴트 턴 JSON으로 직렬화한다({"questions":[...]}). */
  /** 단건 문항을 JSON으로 직렬화한다(재생성 비교 로그용). 실패 시 null. */
  private String toJson(AIProblem problem) {
    try {
      return objectMapper.writeValueAsString(problem);
    } catch (Exception e) {
      log.warn("재생성 로그용 문항 직렬화 실패", e);
      return null;
    }
  }

  private String serializeProblems(List<BatchEntry> batch) {
    try {
      List<AIProblem> problems = batch.stream().map(BatchEntry::problem).toList();
      return objectMapper.writeValueAsString(Map.of("questions", problems));
    } catch (Exception e) {
      log.warn("배치 문항 직렬화 실패. 빈 히스토리로 대체.", e);
      return "{\"questions\":[]}";
    }
  }

  /** Phase 2 사용자 프롬프트 — 직전 배치 문항 번호를 명시하고 선지 순서 유지 해설을 요청한다. */
  private String buildExplanationPrompt(List<BatchEntry> batch) {
    String numbers =
        batch.stream().map(e -> String.valueOf(e.number())).collect(Collectors.joining(", "));
    return "직전 턴에서 생성한 위 문항들의 각 선지에 대한 해설을 작성하라.\n"
        + "- 위 문항들의 번호는 제시된 순서대로 각각 ["
        + numbers
        + "] 이다. 이 번호를 explanations[].number 로 사용하라.\n"
        + "- selectionExplanations 는 각 문항의 제시된 선지 순서를 그대로 유지한다(선지 개수와 동일한 길이).\n"
        + "- 강의노트(PDF) 범위 안에서 정답의 근거와 각 오답이 틀린 이유를 선지별로 구체적으로 설명하라.";
  }

  /** 저장된 배치 1건: (세트 내 번호, 최종 정렬된 문항). */
  private record BatchEntry(int number, AIProblem problem) {}
}
