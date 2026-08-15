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
 * 경량 모델(flash-lite)로 문항 품질을 이진 판정하는 검증기. 필수 항목(실격 사유 부재·사용자 지시 반영)과 유형별 항목을 검사한다. 판정 항목·엄격도는
 * QualityProperties(criteria, FR-011)에서 읽는다.
 *
 * <p>검증관에게는 생성 지침(GuideLine)이 아니라 <b>관찰 가능한 실격 사유</b>만 준다({@link #DISQUALIFIERS}). 생성 지침을 판정 기준으로
 * 주면 검증이 "생성 지시를 지켰는가" 검사로 수렴해 독립 판정이 되지 않는다.
 *
 * <p>aiServer 회로차단으로 장애를 격리하며, 검증 불가(회로 차단·AI 오류) 시 UNVERIFIABLE로 폴백한다.
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
    this.cacheManager = new GeminiContextCacheManager(chatModel, metricsRecorder);
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
              buildSystemPrompt(resolveQuizType(request.quizType()), request.mode(), false));
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
  public Optional<CacheRef> createPass1Cache(String pdfUri, String quizType) {
    // 검증 루브릭(PDF 대조 지시 포함)+PDF 원문을 캐시에 담는다. 세션 내 quizType·language·criteria가 고정이라
    // 루브릭도 고정 → 세트 전 문항 검증이 한 캐시를 재사용한다. 검증 모델(verifyModel)로 캐시를 생성한다.
    String systemPrompt = buildSystemPrompt(resolveQuizType(quizType), Mode.PASS_1, true);
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
  private String buildSystemPrompt(QuizType quizType, Mode mode, boolean pdfGrounded) {
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
        1. 실격 사유 부재: 아래 '실격 사유'에 해당하는 것이 하나도 없는가.
        2. 사용자 지시 반영: customInstruction이 appliedInstruction/문항에 정확히 반영됐는가(지시가 없으면 통과).

        # 검증 항목 및 엄격도 (운영자 설정)
        """
        + buildCriteria(quizType)
        + pass2
        + """

        # 실격 사유 (하나라도 해당하면 미달 — 선지·질문문의 문면만 보고 판정한다)
        """
        + DISQUALIFIERS.getOrDefault(quizType, DISQUALIFIERS.get(QuizType.MULTIPLE));
  }

  /**
   * 유형별 실격 사유. 생성 GuideLine을 그대로 붙이던 자리를 대체한다.
   *
   * <p>검증관에게 "무엇을 만들어라"(생성 지침)를 주면 검증이 생성 지시 준수 검사로 수렴해 독립 판정이 되지 않는다. 여기에는 **관찰 가능한 실격 사유만** 둔다 —
   * 3인 평가에서 확정된 감점 패턴은 전부 문면을 세면 판정되는 형태였다.
   */
  private static final Map<QuizType, String> DISQUALIFIERS =
      Map.of(
          QuizType.MULTIPLE,
              """
              1. 정답이 오답들보다 눈에 띄게 길다.
              2. 절대어(항상·모든·절대로·반드시·오직)가 오답에만 쓰이고 정답만 조건부로 서술된다.
                 — 정답과 오답 양쪽에 고르게 나타나면 정답 단서가 아니므로 해당하지 않는다.
              3. 정답 선지가 강의노트 문장을 그대로 옮겼다(축자 복사). 문자열 조회만으로 정답이 결정된다.
              4. 질문문이 거짓 전제를 단언한다(예: "오류가 하나 있다"고 못박았는데 정답은 "오류가 없다").
              5. 아무도 그렇게 믿지 않을 오답이 있다 — 그 선지를 고른 사람의 잘못된 지식 상태를
                 한 문장으로 적을 수 없으면 해당한다.
              6. "X 하는 대신 Y를 감수한다" 형식에서 오답의 Y만 실질 손해이고 정답의 Y는 손해가 아니거나 이득이다.
              7. 질문문의 특징적 단어가 정답 선지에만 반복된다.
              """,
          QuizType.OX,
              """
              1. 절대어(항상·모든·절대로·언제나)가 있고 정답이 거짓이거나, 완화어(대개·보통·때때로)가 있고 정답이 참이다
                 — 절대어의 존재 자체가 아니라 **정답 방향과의 상관**이 실격 사유다.
              2. 한 진술에 독립적으로 참·거짓을 판정할 명제가 둘 이상 들어 있다.
              3. 진술이 강의노트 문장을 그대로 옮겼다(축자 복사).
              4. 조건에 따라 참일 수도 거짓일 수도 있어 진리값이 하나로 결정되지 않는다.
              5. 지엽적 수치·고유명사 하나만 확인하면 끝나는 사소한 진술이다.
              6. 가치·해석 주장인데 누구의 견해인지 밝히지 않았다.
              7. 정답이 거짓인데 무엇이 왜 틀렸는지 해설에 없다.
              """,
          QuizType.BLANK,
              """
              1. 빈칸 주변 문맥만으로 다른 답이 들어가도 참인 문장이 된다(정답이 하나로 결정되지 않는다).
              2. 해당 지식을 몰라도 문장 다른 부분의 반복·정의문 구조로 빈칸이 채워진다.
              3. 관사·단복수·시제나 빈칸 길이가 정답 후보를 좁혀 준다.
              4. 지워진 단어가 그 문장의 중심 개념이 아니라 부수적 수식어다.
              5. 한 문장에 빈칸이 셋 이상이거나 정답이 여러 어절이라 문장이 무너졌다.
              6. 빈칸이 문장 첫머리에 있어 무엇을 묻는지 읽기 전에 답을 요구한다.
              7. 문장이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """,
          QuizType.ESSAY,
              """
              1. 채점기준표가 질문문에 없는 것을 요구한다(예: "예시 N개 이상", 특정 서술 형식).
                 지시하지 않은 것으로 감점하게 만드는 기준은 그 자체로 실격이다.
              2. 질문문이 서술 범위를 한정하지 않아 학습자마다 다른 것을 쓰게 된다.
              3. 모범답안·채점기준표·질문문 셋 중 어느 한 쌍이 어긋난다
                 (질문↔답 / 답↔기준표 / 질문↔기준표 중 어디인지 밝힐 수 있어야 한다).
              4. 채점기준의 충족 조건이 관찰 불가능하다("논리적으로 서술", "체계적으로 분석").
              5. 선택형으로 충분히 측정되는 것을 서술형으로 냈다(단순 정의·나열로 답이 끝난다).
              6. 모범답안이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """,
          QuizType.REAL_BLANK,
              """
              1. 빈칸 주변 문맥만으로 다른 답이 들어가도 참인 문장이 된다(정답이 하나로 결정되지 않는다).
              2. 해당 지식을 몰라도 문장 다른 부분의 반복·정의문 구조로 빈칸이 채워진다.
              3. 관사·단복수·시제가 정답 후보를 좁혀 준다.
              4. 지워진 단어가 그 문장의 중심 개념이 아니라 부수적 수식어다.
              5. 허용 정답 목록에 정답과 뜻이 다른 표현(인접·상위·하위 개념, 오탈자)이 들어 있다.
              6. 문장이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """);

  /** 검증 항목명 → 검증관이 무엇을 점검해야 하는지에 대한 설명. 경량 모델이 항목을 정확히 적용하도록 프롬프트에 함께 제공한다. */
  private static final Map<String, String> CRITERION_DESCRIPTIONS =
      Map.ofEntries(
          Map.entry("construction-strategy", "위 '실격 사유'에 해당하는 것이 하나도 없는가."),
          Map.entry(
              "instruction-application",
              "사용자 지시(customInstruction)가 문항·appliedInstruction에 정확히 반영됐는가."),
          Map.entry("single-correct-answer", "정답이 유일한가(복수 정답·정답 없음이 아님)."),
          Map.entry("answer-grounded-in-source", "정답이 강의노트 원문에 근거하는가(첨부 PDF가 있으면 원문과 직접 대조)."),
          Map.entry("distractors-plausible", "오답이 진지하게 고민할 만큼 그럴듯한가(명백한 극단·환상 진술이 아님)."),
          Map.entry("no-outside-knowledge", "강의노트 범위 밖 지식 없이 풀 수 있는가(환각·외부지식 보강 배제)."),
          Map.entry(
              "shortcut-prevention",
              "내용을 몰라도 선지의 형태만으로 정답을 고를 수 있으면 위반. 아래 셋을 각각 확인한다."
                  + " ① 정답 길이: 정답이 오답들보다 눈에 띄게 길면 위반. 길이는 고르게 맞추거나, 길어야 한다면 정답이 아닌 선지가 길어야 한다."
                  + " ② 절대어 편중: '항상·모든·절대로·반드시·오직'처럼 예외 없음을 단언하는 표현이 오답에만 쓰이고 정답만 조건부로 서술되면 위반."
                  + " 절대어가 있다는 사실 자체는 위반이 아니다 — 정답과 오답 양쪽에 고르게 나타나면 정답 단서가 되지 않으므로 통과."
                  + " ③ 어조·위험도 대칭: 정답만 유독 온건하거나 균형 잡힌 서술이면 위반. 트레이드오프형에서 오답이"
                  + " '런타임 오류·기동 실패·데이터 전면 손실' 같은 파국을 스스로 선언해 소거되면 위반 — 선지들의 감수 위험도가 대칭이어야 한다."),
          Map.entry(
              "cognitive-depth",
              "정답이 강의노트 문장에 1:1로 직접 대응하거나, 표/다이어그램의 단일 셀 기본값(예: length=255) 하나를 암기로 알면 즉시 풀리면"
                  + " 위반. 정답 도출에 여러 항목의 교차 대조나 다단계 추론이 필요해야 한다."),
          Map.entry("model-answer-basis", "[ESSAY] 모범답안이 원문에 근거하는가."),
          Map.entry("rubric-consistency", "[ESSAY] 질문↔모범답안↔채점 루브릭 3자가 정합하는가."));

  /** ESSAY 전용 검증 항목 — 객관형 프롬프트에 실리면 검증관이 없는 산출물을 찾게 되므로 걸러낸다. */
  private static final java.util.Set<String> ESSAY_ONLY_CRITERIA =
      java.util.Set.of("model-answer-basis", "rubric-consistency");

  /** 객관형(MULTIPLE·OX·BLANK·REAL_BLANK) 전용 검증 항목 — ESSAY 는 선지가 없어 판정할 수 없다. */
  private static final java.util.Set<String> CHOICE_ONLY_CRITERIA =
      java.util.Set.of("single-correct-answer", "distractors-plausible", "shortcut-prevention");

  private String buildCriteria(QuizType quizType) {
    Map<String, String> criteria = properties.getCriteria();
    if (criteria == null || criteria.isEmpty()) {
      return "- (설정된 항목 없음 — 필수 항목만 적용)\n";
    }
    boolean essay = quizType == QuizType.ESSAY;
    return criteria.entrySet().stream()
        .filter(e -> e.getValue() != null && !"off".equalsIgnoreCase(e.getValue()))
        .filter(
            e ->
                essay
                    ? !CHOICE_ONLY_CRITERIA.contains(e.getKey())
                    : !ESSAY_ONLY_CRITERIA.contains(e.getKey()))
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

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
