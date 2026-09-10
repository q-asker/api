package com.icc.qasker.ai.service.quality;

import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.metric.GeminiMetricsRecorder;
import com.icc.qasker.ai.properties.QualityProperties;
import com.icc.qasker.ai.service.QualityVerifier;
import com.icc.qasker.ai.service.quality.prompt.QualityGuideLine;
import com.icc.qasker.ai.service.quality.prompt.QualityRequestPrompt;
import com.icc.qasker.ai.structure.GeminiVerificationResponse;
import com.icc.qasker.ai.support.GeminiContextCacheManager;
import com.icc.qasker.global.quiz.QuizType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
 * <p>검증관에게는 생성 지침(GuideLine)이 아니라 <b>관찰 가능한 실격 사유</b>만 준다({@link QualityGuideLine}). 생성 지침을 판정 기준으로
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
    QualityGuideLine.assertAllTypesCovered();
  }

  @Override
  @CircuitBreaker(name = "aiServer", fallbackMethod = "verifyFallback")
  public QualityVerdict verify(QualityVerificationRequest request) {
    long startMs = System.currentTimeMillis();

    UserMessage userMessage = new UserMessage(QualityRequestPrompt.build(request, objectMapper));
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
              QualityGuideLine.build(
                  request.quizType(), request.mode(), false, properties.getCriteria()));
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
  public Optional<CacheRef> createPass1Cache(String pdfUri, QuizType quizType) {
    // 검증 루브릭(PDF 대조 지시 포함)+PDF 원문을 캐시에 담는다. 세션 내 quizType·language·criteria가 고정이라
    // 루브릭도 고정 → 세트 전 문항 검증이 한 캐시를 재사용한다. 검증 모델(verifyModel)로 캐시를 생성한다.
    String systemPrompt =
        QualityGuideLine.build(quizType, Mode.PASS_1, true, properties.getCriteria());
    return cacheManager.create(
        "Pass 1 검증", properties.getVerifyModel(), systemPrompt, pdfUri, PASS1_CACHE_TTL);
  }

  @Override
  public void deletePass1Cache(CacheRef cacheRef) {
    cacheManager.delete("Pass 1 검증", cacheRef == null ? null : cacheRef.name());
  }
}
