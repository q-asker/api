package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.ChunkPlanner;
import com.icc.qasker.ai.service.support.GeminiContextCacheManager;
import com.icc.qasker.ai.service.support.GeminiContextCacheManager.CacheRef;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.StreamingJsonArrayExtractor;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import com.icc.qasker.global.error.CustomException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * 청크형(MULTIPLE/BLANK/OX) 퀴즈 오케스트레이터의 공통 골격 — 배치 인터리빙 1-패스 생성.
 *
 * <p>chunk-size 단위 배치마다 <b>문제+선지별 해설을 한 호출</b>로 스트리밍 생성한다. 전체 지침(문제+해설)과 공통 문서(PDF)를 <b>단일 Vertex
 * 컨텍스트 캐시</b>에 담아 청크·재생성 호출 간 재사용하고 생성 종료 시 삭제한다(재과금 절감). 모든 호출은 하나의 멀티턴 대화 스레드에 누적되어 배치 간 중복을
 * 방지한다. 캐시 생성 불가 시(비지원 ChatModel·최소 토큰 미달 등) PDF를 매 호출 전송하는 폴백으로 자동 강등한다.
 *
 * <p>문항이 완성될 때마다 선지를 {@link #arrangeSelections}로 최종 정렬(선지에 인라인 해설이 붙어 있어 함께 이동)한 뒤 {@link
 * QuizBatchSink#saveProblem}로 즉시 저장·통지한다. 저장 시 sink가 인라인 해설을 해설 마크다운으로 조립한다(별도 해설 단계 없음).
 *
 * <p>배치가 산출물 0건으로 실패하면 직전까지 저장한 배치만 남기고 루프를 종료한다(부분 저장·완료).
 *
 * <p>한 요청분의 실행 상태(캐시·대화 스레드·보류 큐·누적 메트릭)는 요청마다 새로 만드는 {@link Session}에 담긴다. 오케스트레이터 빈 자체는 무상태이므로 이
 * 클래스의 필드에는 협력 빈만 두고, 세션 상태는 {@code generateQuiz} 호출마다 격리한다(스레드 안전).
 */
@Slf4j
public abstract class AbstractChunkedQuizOrchestrator implements QuizTypeOrchestrator {

  /** 비동기 생성 게이트 검증의 동시 실행 상한(외부 검증 모델 호출 버스트를 제한). */
  private static final int VERIFY_CONCURRENCY = 8;

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
    this.cacheManager = new GeminiContextCacheManager(chatModel);
  }

  /** 문항 최대 선택지 수. 초과 문항은 drop 된다 (MULTIPLE/BLANK=4, OX=2). */
  protected abstract int maxSelectionCount();

  /** 청크 K(K≥2) 사용자 프롬프트 꼬리에 붙는 타입별 중복 회피 지침 문구. */
  protected abstract String dedupInstruction();

  /**
   * 선지를 최종 저장 순서로 정렬한다(MULTIPLE/BLANK 셔플, OX O-우선 정규화). 저장 순서 = 대화 히스토리 순서 = 선지에 인라인으로 붙는 해설 정렬
   * 기준이므로 이 시점에 확정한다.
   */
  protected abstract List<AISelection> arrangeSelections(List<AISelection> selections);

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    return new Session(request).run();
  }

  /**
   * 한 요청분의 인터리빙 실행 상태. 협력 빈은 바깥 인스턴스에서, 요청별 상태(캐시·대화 스레드·보류 큐·누적 메트릭)는 이 인스턴스에서 관리한다. {@code
   * generateQuiz} 호출마다 새로 만들어지므로 오케스트레이터 빈은 무상태로 유지된다.
   */
  private final class Session {

    // 협력 객체·불변 설정 (생성자에서 1회 확정)
    private final GenerationRequestToAI request;
    private final QuizBatchSink sink;
    private final int quizCount;
    private final QuizType quizType;
    // 단일 생성 프롬프트(1-패스): 문제+선지별 해설을 한 호출에 생성한다.
    private final String genGuideLine;
    private final FileMetadata metadata;
    private final Media pdfMedia;
    // 컨텍스트 캐시: 전체 지침(문제+해설)+PDF를 1개 캐시. 생성·재생성이 공유한다. 실패 시 null → 폴백.
    private final CacheRef genCache;
    // Pass 1 검증 캐시(검증 루브릭+PDF 원문): 세션당 1개. 실패 시 null → 원문 대조 없는 검증.
    private final String verifyCacheName;
    private final String tag = getSupportedType();
    private final long startNanos = System.nanoTime();

    // 누적 상태
    // 멀티턴 대화 스레드 — U/A 턴만 누적. 캐시 사용 시 system·PDF는 캐시에, 폴백 시 각 요청이 phase별 system(+PDF)을 붙인다.
    private final List<Message> conversation = new ArrayList<>();
    // 생성 게이트에서 미달 판정된 문항의 보류 큐. 여러 검증 워커가 동시에 add 하므로 락프리 동시 큐를 쓰고,
    // 청크 루프 종료(배리어) 후 단일 스레드에서 FIFO로 1회 재생성 소진한다.
    private final Queue<HeldProblem> heldQueue = new ConcurrentLinkedQueue<>();
    private final DoubleAdder totalCost = new DoubleAdder();
    private final AtomicLong firstNanos = new AtomicLong(0);
    private final AtomicLong lastNanos = new AtomicLong(0);
    // delivered = 검증 통과·저장된 문항 수. submitted = 검증에 제출한(생성) 문항 수(파싱 중단·청크 진행 판단 기준).
    private final AtomicInteger delivered = new AtomicInteger(0);
    private final AtomicInteger submitted = new AtomicInteger(0);

    // 생성 스트림 소비와 게이트 검증을 분리하는 비동기 인프라.
    // 스트림 콜백은 문항을 verifyExecutor에 제출하고 즉시 리턴한다(스트림 논블로킹).
    // 워커는 verifySlots로 동시성을 제한한 채 검증→통과: 저장·통지 / 미달: 보류 큐.
    private final ExecutorService verifyExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore verifySlots = new Semaphore(VERIFY_CONCURRENCY);
    private final List<CompletableFuture<Void>> verifyFutures = new CopyOnWriteArrayList<>();

    private Session(GenerationRequestToAI request) {
      this.request = request;
      this.sink = request.sink();
      this.quizCount = request.quizCount();
      this.quizType = QuizType.valueOf(request.strategyValue());
      this.genGuideLine = quizType.getSystemGuideLine(request.language());
      this.metadata = resolvePdf(request);
      this.pdfMedia =
          new Media(MimeTypeUtils.parseMimeType("application/pdf"), URI.create(metadata.uri()));
      // PDF + 시스템 가이드라인은 어떤 세션이든 공유하는 캐시, 메인 모델이 사용하는 캐시
      this.genCache =
          cacheManager
              .create(tag, genGuideLine, metadata.uri(), aiProperties.getCacheTtl())
              .orElse(null);
      // 검증 모델이 사용하는 캐시
      this.verifyCacheName =
          qualityGate
              .createPass1Cache(metadata.uri(), quizType.name(), request.language())
              .orElse(null);
    }

    private int run() {
      List<Integer> chunkPlan =
          ChunkPlanner.plan(quizCount, aiProperties.getChunk().getChunkSize());
      log.info(
          "{} 인터리빙 분할: 요청={}문항, chunk-size={}, 청크={}개",
          tag,
          quizCount,
          aiProperties.getChunk().getChunkSize(),
          chunkPlan.size());

      int chunksDone = 0;
      try {
        for (int chunkIndex = 0; chunkIndex < chunkPlan.size(); chunkIndex++) {
          if (submitted.get() >= quizCount) break;
          int chunkSize = chunkPlan.get(chunkIndex);

          List<AIProblem> generated = runGenerationPhase(chunkIndex, chunkSize);

          if (generated.isEmpty()) {
            log.warn("{} 청크 #{} 산출물 없음 — 부분 저장·완료.", tag, chunkIndex);
            break;
          }
          chunksDone++;
        }

        // 배리어: 비동기 검증이 모두 정착해야 heldQueue·delivered가 확정된다.
        awaitVerifications();

        // 보류된 미달 문항을 세션 내에서 1회 재생성해 통과분을 채운다(원문 컨텍스트·대화 스레드 활용).
        processHeldQueue();
      } catch (CustomException e) {
        awaitVerifications();
        if (delivered.get() == 0) throw e;
        log.warn("{} 인터리빙 도중 비즈니스 오류. {}문항 보존.", tag, delivered.get(), e);
      } catch (Exception e) {
        awaitVerifications();
        if (delivered.get() == 0) throw new GeminiInfraException("Gemini 인프라 장애", e);
        log.warn("{} 인터리빙 도중 인프라 오류. {}문항 보존.", tag, delivered.get(), e);
        metricsRecorder.recordStreamingTimeout(tag);
      } finally {
        // 예외로 중단됐어도 인플라이트 검증을 마저 정착시킨 뒤(멱등) 캐시를 삭제한다.
        awaitVerifications();
        verifyExecutor.shutdown();
        cacheManager.delete(tag, genCache == null ? null : genCache.name());
        qualityGate.deletePass1Cache(verifyCacheName);
      }

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

    /**
     * 1-패스 생성: 문제+선지별 해설을 한 호출로 스트리밍 생성한다. 문항이 완성될 때마다 검증을 비동기로 제출하고 즉시 다음 청크를 이어받는다(스트림 논블로킹). 검증
     * 통과 순서대로 저장·통지되므로 사용자가 먼저 받는 문항이 낮은 번호를 갖는다. 반환값은 이 청크에서 생성된(검증 이전) 문항 목록으로, 대화 히스토리와 청크 진행
     * 판단에 쓰인다.
     */
    private List<AIProblem> runGenerationPhase(int chunkIndex, int chunkSize) {
      String userPrompt =
          quizType.generateRequestPrompt(
              request.referencePages(), chunkSize, request.customInstruction());
      if (chunkIndex > 0) {
        userPrompt = userPrompt + dedupInstruction();
      }

      // 캐시가 있으면 PDF는 캐시 프리픽스에 있으므로 첨부하지 않는다. 폴백(캐시 없음) 시 첫 사용자 턴에만 첨부한다.
      UserMessage.Builder ub = UserMessage.builder().text(userPrompt);
      if (genCache == null && chunkIndex == 0) {
        ub.media(pdfMedia);
      }
      UserMessage phase1User = ub.build();

      // 캐시 사용 시 system·PDF는 캐시에 있으므로 요청엔 대화 턴만. 폴백 시 전체 지침(문제+해설)을 prepend.
      List<Message> messages = new ArrayList<>();
      if (genCache == null) {
        messages.add(new SystemMessage(genGuideLine));
      }
      messages.addAll(conversation);
      messages.add(phase1User);

      // 전체 스키마(선지별 해설 포함) — 문제·해설을 한 응답에 생성한다. 저장 시 sink가 인라인 해설을 마크다운으로 조립한다.
      String schema = GeminiResponseSchema.forInstruction(request.customInstruction());
      Prompt prompt = new Prompt(messages, buildOptions(schema, genCache));

      // 이 청크에서 생성된(검증 이전) 문항 — 생성 순서. 대화 히스토리 구성과 청크 진행 판단에 쓴다.
      // 스트림 콜백(단일 리액터 스레드)에서만 add 하고, streamInto 종료(blockLast) 이후 읽으므로 가시성이 보장된다.
      List<AIProblem> generated = new ArrayList<>();
      StreamingJsonArrayExtractor<GeminiQuestion> extractor =
          new StreamingJsonArrayExtractor<>(
              objectMapper,
              GeminiQuestion.class,
              question -> {
                if (submitted.get() >= quizCount) return;
                if (question.selections() != null
                    && question.selections().size() > maxSelectionCount()) return;

                AIProblem arranged = toArrangedProblem(question, metadata.sourcePages());
                submitted.incrementAndGet();
                generated.add(arranged);

                // 생성 게이트를 비동기로 제출하고 즉시 리턴한다 — 스트림 소비를 검증이 붙잡지 않는다.
                verifyFutures.add(submitVerification(arranged));
              },
              tag);

      streamInto(prompt, extractor, chunkIndex);

      // 어시스턴트 턴 = 이 청크에서 생성된 전체 문항(검증 결과와 무관). 검증을 기다리지 않고 다음 청크의 중복 회피 컨텍스트를 확정한다.
      conversation.add(phase1User);
      conversation.add(new AssistantMessage(serializeProblems(generated)));
      return generated;
    }

    /**
     * 생성 게이트 검증을 비동기로 수행한다. 통과·검증불가면 저장·통지하고, 미달(v1)이면 보류 큐에 넣는다(청크 루프 종료 후 1회 재생성). 저장 순번은 이 워커가
     * saveProblem을 호출하는 순서 = 검증 통과 순서이므로, 먼저 통과한 문항이 낮은 번호를 받는다.
     */
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
                        verifyCacheName);
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

    /** 통과 문항을 저장·통지하고 v1 로그를 기록한다. 여러 검증 워커가 동시에 호출하므로 sink는 스레드 안전해야 한다(consumerLock 직렬화). */
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

    /** 제출된 모든 비동기 검증이 정착할 때까지 대기한다(멱등 — 여러 번 호출해도 안전). */
    private void awaitVerifications() {
      try {
        CompletableFuture.allOf(verifyFutures.toArray(CompletableFuture[]::new)).join();
      } catch (Exception e) {
        log.warn("{} 검증 대기 중 오류", tag, e);
      }
    }

    private void streamInto(
        Prompt prompt, StreamingJsonArrayExtractor<GeminiQuestion> extractor, int chunkIndex) {
      Flux<ChatResponse> stream = chatModel.stream(prompt);
      stream
          .doOnNext(
              response -> {
                String text = extractText(response);
                if (text != null) extractor.feed(text);
                recordUsage(response, tag + " chunk #" + chunkIndex);
              })
          // 대기 상한은 OkHttp 시한(connect/read/call, gemini-http 설정)이 전담한다 — 동기 풀 구조라
          // blockLast의 Duration은 발화 불능이므로 두지 않는다.
          .blockLast();
    }

    private void recordUsage(ChatResponse response, String label) {
      if (response.getMetadata().getUsage().getCompletionTokens() > 0) {
        double cost =
            metricsRecorder.recordChunkUsage(label, startNanos, response.getMetadata().getUsage());
        totalCost.add(cost);
      }
    }

    /**
     * 보류된 미달 문항을 세션 내에서 1회씩 재생성한다(FR: 1회 캡). 개선본(v2)은 검증 없이 저장하고, 품질 로그엔 v1·v2를 함께 기록한다(사후 Pass 2가
     * v2를 판정). 재생성 불가→제외. 이미 목표 문항 수를 채웠으면 중단한다.
     */
    private void processHeldQueue() {
      if (heldQueue.isEmpty()) {
        return;
      }
      log.info("{} 보류 문항 재생성 시작: {}건", tag, heldQueue.size());
      for (HeldProblem held : heldQueue) {
        if (delivered.get() >= quizCount) {
          break;
        }
        try {
          AIProblem v2 = regenerateOne(held);
          if (v2 == null) {
            continue;
          }
          // v2는 검증 없이 저장한다(사후 Pass 2가 판정). 품질 로그엔 미달 원본(v1)과 개선본(v2)을 함께 기록해 전후 비교를 남긴다.
          int number = sink.saveProblem(v2);
          sink.recordV1(number, held.problem(), held.feedback());
          sink.recordV2(number, v2);
          delivered.incrementAndGet();
        } catch (Exception e) {
          // 재생성 불가 → 제외(문항 수 축소).
          log.warn("{} 보류 문항 재생성 실패 — 제외", tag, e);
        }
      }
    }

    /** 보류 문항 1건을 재생성한다. 살아있는 대화 스레드(원문 컨텍스트)에 개선 지시를 얹어 개선 문항 1개를 산출한다. */
    private AIProblem regenerateOne(HeldProblem held) {
      List<Message> messages = new ArrayList<>();
      if (genCache == null) {
        messages.add(new SystemMessage(genGuideLine));
      }
      messages.addAll(conversation);
      messages.add(new UserMessage(buildRegenerationPrompt(held)));

      String schema = GeminiResponseSchema.forInstruction(request.customInstruction());
      ChatResponse response = chatModel.call(new Prompt(messages, buildOptions(schema, genCache)));
      recordUsage(response, tag + " regenerate");

      String text = extractText(response);
      if (text == null) {
        return null;
      }
      GeminiResponse parsed = new BeanOutputConverter<>(GeminiResponse.class).convert(text);
      if (parsed.questions() == null || parsed.questions().isEmpty()) {
        return null;
      }
      GeminiQuestion q = parsed.questions().getFirst();
      if (q.selections() != null && q.selections().size() > maxSelectionCount()) {
        return null;
      }
      return toArrangedProblem(q, metadata.sourcePages());
    }
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

  private static String extractText(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return null;
    } else {
      response.getResult();
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
        mapped.appliedInstruction());
  }

  /** 생성 게이트에서 미달 판정돼 보류된 문항(v1)과 개선 피드백. */
  private record HeldProblem(AIProblem problem, String feedback) {}

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

  /** 생성된 문항을 어시스턴트 턴 JSON으로 직렬화한다({"questions":[...]}). */
  private String serializeProblems(List<AIProblem> problems) {
    try {
      return objectMapper.writeValueAsString(Map.of("questions", problems));
    } catch (Exception e) {
      log.warn("배치 문항 직렬화 실패. 빈 히스토리로 대체.", e);
      return "{\"questions\":[]}";
    }
  }
}
