package com.icc.qasker.ai.service.quality;

import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.properties.QualityProperties;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.ai.service.support.GeminiContextCacheManager;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiVerificationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 경량 모델(flash-lite)로 문항 품질을 이진 판정하는 검증기. 필수 항목(구성전략 GuideLine 부합·사용자 지시 반영)과 유형별 항목을 검사한다. 판정
 * 항목·엄격도는 QualityProperties(criteria, FR-011)에서 읽고, 유형별 GuideLine은 QuizType에서 해석한다. aiServer 회로차단으로
 * 장애를 격리하며, 검증 불가(회로 차단·AI 오류) 시 UNVERIFIABLE로 폴백한다.
 */
@Slf4j
@Service
public class QualityVerifierImpl implements QualityVerifier {

  /** Pass 1 검증 캐시 TTL — 한 세트 생성 세션을 커버(생성 캐시와 동일). */
  private static final Duration PASS1_CACHE_TTL = Duration.ofMinutes(15);

  private final ChatModel chatModel;
  private final GeminiMetricsRecorder metricsRecorder;
  private final QualityProperties properties;
  private final ObjectMapper objectMapper;
  private final String verifySchema;
  private final GeminiContextCacheManager cacheManager;

  public QualityVerifierImpl(
      ChatModel chatModel,
      GeminiMetricsRecorder metricsRecorder,
      QualityProperties properties,
      ObjectMapper objectMapper) {
    this.chatModel = chatModel;
    this.metricsRecorder = metricsRecorder;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.verifySchema = new BeanOutputConverter<>(GeminiVerificationResponse.class).getJsonSchema();
    this.cacheManager = new GeminiContextCacheManager(chatModel);
  }

  @Override
  @CircuitBreaker(name = "aiServer", fallbackMethod = "verifyFallback")
  public QualityVerdict verify(QualityVerificationRequest request) {
    long startMs = System.currentTimeMillis();

    UserMessage userMessage = new UserMessage(buildUserPrompt(request));
    GoogleGenAiChatOptions.Builder options =
        GoogleGenAiChatOptions.builder()
            .model(properties.getVerifyModel())
            .responseMimeType("application/json")
            .responseSchema(verifySchema);

    List<Message> messages;
    if (request.cacheRef() != null) {
      // 캐시 사용: 검증 루브릭+PDF 원문은 캐시 프리픽스에 있으므로 요청엔 대화 턴만
      // (Vertex는 캐시 사용 시 요청 systemInstruction 금지). 검증기가 PDF 원문과 직접 대조한다.
      options.useCachedContent(true).cachedContentName(request.cacheRef().name());
      messages = List.of(userMessage);
    } else {
      // 폴백: 루브릭을 systemInstruction으로 붙이고 PDF 대조 없이 검증(현행).
      SystemMessage systemMessage =
          new SystemMessage(
              buildSystemPrompt(
                  resolveQuizType(request.quizType()),
                  resolveLanguage(request.language()),
                  request.mode(),
                  false));
      messages = List.of(systemMessage, userMessage);
    }

    ChatResponse chatResponse = chatModel.call(new Prompt(messages, options.build()));
    String responseText = chatResponse.getResult().getOutput().getText();
    GeminiVerificationResponse parsed =
        new BeanOutputConverter<>(GeminiVerificationResponse.class).convert(responseText);

    recordMetrics(startMs, chatResponse);

    if (parsed == null) {
      throw new IllegalStateException("검증 응답 파싱 실패");
    }
    return parsed.passed() ? QualityVerdict.pass() : QualityVerdict.below(parsed.feedback());
  }

  /** 회로 차단·AI 오류 시 폴백 — 검증 불가로 처리한다(FR-010). 생성 흐름을 막지 않는다. */
  @SuppressWarnings("unused")
  private QualityVerdict verifyFallback(QualityVerificationRequest request, Throwable t) {
    log.warn("[품질 검증 폴백] 검증 불가 처리 quizType={}, 원인={}", request.quizType(), t.toString());
    metricsRecorder.recordVerifyFailure();
    return QualityVerdict.unverifiable("검증기 오류·회로 차단으로 검증 불가");
  }

  private void recordMetrics(long startMs, ChatResponse chatResponse) {
    long elapsedMs = System.currentTimeMillis() - startMs;
    Usage usage = chatResponse.getMetadata().getUsage();
    long cachedTokens =
        usage instanceof GoogleGenAiUsage g && g.getCachedContentTokenCount() != null
            ? g.getCachedContentTokenCount()
            : 0;
    long nonCachedInput = Math.max(0, usage.getPromptTokens() - cachedTokens);
    long output = usage.getCompletionTokens();
    double cost =
        nonCachedInput * properties.getPriceInputPer1m() / 1_000_000
            + cachedTokens * properties.getPriceCacheReadPer1m() / 1_000_000
            + output * properties.getPriceOutputPer1m() / 1_000_000;
    metricsRecorder.recordVerify(elapsedMs, nonCachedInput, output, cost);
  }

  @Override
  public Optional<CacheRef> createPass1Cache(String pdfUri, String quizType, String language) {
    // 검증 루브릭(PDF 대조 지시 포함)+PDF 원문을 캐시에 담는다. 세션 내 quizType·language·criteria가 고정이라
    // 루브릭도 고정 → 세트 전 문항 검증이 한 캐시를 재사용한다. 검증 모델(verifyModel)로 캐시를 생성한다.
    String systemPrompt =
        buildSystemPrompt(resolveQuizType(quizType), resolveLanguage(language), Mode.PASS_1, true);
    return cacheManager.create(
        "Pass 1 검증", properties.getVerifyModel(), systemPrompt, pdfUri, PASS1_CACHE_TTL);
  }

  @Override
  public void deletePass1Cache(CacheRef cacheRef) {
    cacheManager.delete("Pass 1 검증", cacheRef == null ? null : cacheRef.name());
  }

  /**
   * 검증관 시스템 프롬프트를 만든다. pdfGrounded=true면 첨부 PDF 원문과 직접 대조하도록 지시한다(Pass 1 캐시 검증 — 환각·출처 이탈 탐지).
   * false면 PDF 없이 문항 자체로 판정한다(폴백·Pass 2 현행). 어느 경우든 문항 자체(+첨부 PDF 원문)만 보고 판정한다.
   */
  private String buildSystemPrompt(
      QuizType quizType, String language, Mode mode, boolean pdfGrounded) {
    String grounding =
        pdfGrounded
            ? """
            # 원문 대조 (중요)
            첨부된 PDF가 이 문항의 출처 원문이다. 정답 근거(answer-grounded-in-source)와 범위 밖 지식 여부(no-outside-knowledge)를
            반드시 **첨부 PDF 원문과 직접 대조**해 판정하라. 원문에 없는 사실로만 정답이 성립하면 미달이다.

            """
            : "";

    String pass2 =
        mode == Mode.PASS_2
            ? """

            # 추가 심층 검증 (Pass 2 — 더 엄격)
            - 세트 내 문항 다양성·중복 회피
            - 해설-문항 정합성
            - 인지적 깊이·지름길(shortcut) 풀이 방지
            - 출처 충실성 심화(정답이 강의노트 원문 범위 내 근거로 성립하는가)
            - 재생성 반영 검증: 입력의 priorRoundFeedback(이전 라운드 미달 사유)이 비어있지 않으면, 현재 문항은 그 사유를 고치려고 재생성된 개선본이다. 각 지적이 실제로 해소됐는지 판정하고, feedback에 '어떤 지적이 어떻게 반영/미반영됐는지'를 항목별로 구체적으로 서술한다. 미해소·부분해소가 있으면 미달로 본다.
            """
            : "";

    return """
        # 역할
        당신은 AI가 생성한 %s 문항의 품질을 검수하는 엄격한 검증관이다.
        아래 '검증 항목'을 기준으로 문항을 점검하고, 이진 판정(통과/미달)을 내린다.

        """
            .formatted(quizType.name())
        + grounding
        + """
        # 판정 규칙
        - 필수 항목 중 **하나라도 실패하면 미달(passed=false)**. 모든 필수 항목을 통과해야 통과(passed=true).
        - 가중 점수 합산이 아니라 치명 항목 이진 판정이다.
        - 검증 항목의 엄격도: **strict = 경미한 위반도 미달**, **normal = 명백한 위반만 미달**(애매하면 통과).
        - 미달 시 feedback에 실패 항목과 개선 방향을 구체적으로 적는다. 통과 시 feedback은 빈 문자열.

        # 필수 항목 (전 유형 공통)
        1. 구성전략 부합: 문항(질문·선지 구성)이 아래 유형별 GuideLine의 출제 원칙·패턴에 부합하는가.
        2. 사용자 지시 반영: customInstruction이 appliedInstruction/문항에 정확히 반영됐는가(지시가 없으면 통과).

        # 검증 항목 및 엄격도 (운영자 설정)
        """
        + buildCriteria()
        + pass2
        + """

        # 유형별 출제 GuideLine (구성전략 부합 판단 기준)
        """
        + quizType.getProblemGuideLine(language);
  }

  /** 검증 항목명 → 검증관이 무엇을 점검해야 하는지에 대한 설명. 경량 모델이 항목을 정확히 적용하도록 프롬프트에 함께 제공한다. */
  private static final Map<String, String> CRITERION_DESCRIPTIONS =
      Map.ofEntries(
          Map.entry("construction-strategy", "문항(질문·선지 구성)이 유형별 GuideLine의 출제 패턴·원칙에 부합하는가."),
          Map.entry(
              "instruction-application",
              "사용자 지시(customInstruction)가 문항·appliedInstruction에 정확히 반영됐는가."),
          Map.entry("single-correct-answer", "정답이 유일한가(복수 정답·정답 없음이 아님)."),
          Map.entry("answer-grounded-in-source", "정답이 강의노트 원문에 근거하는가(첨부 PDF가 있으면 원문과 직접 대조)."),
          Map.entry("distractors-plausible", "오답이 진지하게 고민할 만큼 그럴듯한가(명백한 극단·환상 진술이 아님)."),
          Map.entry("no-outside-knowledge", "강의노트 범위 밖 지식 없이 풀 수 있는가(환각·외부지식 보강 배제)."),
          Map.entry(
              "shortcut-prevention",
              "내용을 몰라도 어조·선지 길이·정답의 온건함/균형만으로 정답을 고를 수 있으면 위반. 특히 트레이드오프형에서 오답이"
                  + " '런타임 오류·기동 실패·데이터 전면 손실' 같은 파국을 스스로 선언해 소거되면 위반 — 4개 선지의 감수 위험도가 대칭이어야 한다."),
          Map.entry(
              "cognitive-depth",
              "정답이 강의노트 문장에 1:1로 직접 대응하거나, 표/다이어그램의 단일 셀 기본값(예: length=255) 하나를 암기로 알면 즉시 풀리면"
                  + " 위반. 정답 도출에 여러 항목의 교차 대조나 다단계 추론이 필요해야 한다."),
          Map.entry("model-answer-basis", "[ESSAY] 모범답안이 원문에 근거하는가."),
          Map.entry("rubric-consistency", "[ESSAY] 질문↔모범답안↔채점 루브릭 3자가 정합하는가."));

  private String buildCriteria() {
    Map<String, String> criteria = properties.getCriteria();
    if (criteria == null || criteria.isEmpty()) {
      return "- (설정된 항목 없음 — 필수 항목만 적용)\n";
    }
    return criteria.entrySet().stream()
        .filter(e -> e.getValue() != null && !"off".equalsIgnoreCase(e.getValue()))
        .map(
            e -> {
              String base = "- " + e.getKey() + " (엄격도: " + e.getValue() + ")";
              String desc = CRITERION_DESCRIPTIONS.get(e.getKey());
              return desc != null ? base + ": " + desc + "\n" : base + "\n";
            })
        .collect(Collectors.joining());
  }

  private String buildUserPrompt(QualityVerificationRequest request) {
    // 검증관은 문항 자체(+첨부 PDF 원문)만 보고 독립·비판적으로 판정한다.
    Map<String, Object> payload =
        Map.of(
            "question", nullToEmpty(request.question()),
            "selections", request.selections() == null ? List.of() : request.selections(),
            "modelAnswer", nullToEmpty(request.modelAnswer()),
            "customInstruction", nullToEmpty(request.customInstruction()),
            "appliedInstruction", nullToEmpty(request.appliedInstruction()),
            "priorRoundFeedback", nullToEmpty(request.priorFeedback()));
    return "# 검증 대상 문항\n" + serialize(payload);
  }

  private String serialize(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      return String.valueOf(payload);
    }
  }

  private static QuizType resolveQuizType(String quizType) {
    try {
      return QuizType.valueOf(quizType);
    } catch (IllegalArgumentException | NullPointerException e) {
      return QuizType.MULTIPLE;
    }
  }

  private static String resolveLanguage(String language) {
    return "EN".equalsIgnoreCase(language) ? "EN" : "KO";
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
