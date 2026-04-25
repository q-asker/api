package com.icc.qasker.ai.service.essay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.EssayGradingService;
import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.service.essay.prompt.EssayEvidenceExtractionGuideLine;
import com.icc.qasker.ai.service.essay.prompt.EssayEvidenceExtractionPrompt;
import com.icc.qasker.ai.service.essay.prompt.EssayGradingGuideLine;
import com.icc.qasker.ai.service.essay.prompt.EssayGradingRequestPrompt;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.structure.GeminiEvidenceExtractionResponse;
import com.icc.qasker.ai.structure.GeminiFirstAttemptGradingResponse;
import com.icc.qasker.ai.structure.GeminiGradingResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

/**
 * ESSAY 답안 채점 서비스. 2-pass 파이프라인으로 채점한다.
 *
 * <p>Pass 1 (증거 추출): 학생 답안에서 루브릭 요소별 증거를 원문 그대로 인용한다. Pass 2 (채점): 추출된 증거를 기반으로 충족/부분충족/미충족을 판정한다.
 */
@Slf4j
@Service
public class EssayGradingServiceImpl implements EssayGradingService {

  private static final String GRADING_MODEL = "gemini-3.1-flash-lite-preview";
  private static final double PRICE_INPUT_PER_1M = 0.25;
  private static final double PRICE_OUTPUT_PER_1M = 1.50;

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;
  private final String evidenceSchema;
  private final String firstAttemptSchema;
  private final String defaultSchema;

  /** Pass 결과를 ChatResponse와 함께 전달하기 위한 내부 record. */
  private record PassResult<T>(T result, ChatResponse chatResponse) {}

  public EssayGradingServiceImpl(
      ChatModel chatModel, ObjectMapper objectMapper, GeminiMetricsRecorder metricsRecorder) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.evidenceSchema =
        new BeanOutputConverter<>(GeminiEvidenceExtractionResponse.class).getJsonSchema();
    this.firstAttemptSchema =
        new BeanOutputConverter<>(GeminiFirstAttemptGradingResponse.class).getJsonSchema();
    this.defaultSchema = new BeanOutputConverter<>(GeminiGradingResponse.class).getJsonSchema();
  }

  @Override
  public EssayGradingResult grade(
      String question, String modelAnswer, String rubric, String studentAnswer, int attemptCount) {
    log.info(
        "ESSAY 채점 시작 (2-pass): 질문 길이={}, 답안 길이={}, 시도={}",
        question.length(),
        studentAnswer.length(),
        attemptCount);
    long startMs = System.currentTimeMillis();

    try {
      // Pass 1: 증거 추출
      PassResult<GeminiEvidenceExtractionResponse> pass1;
      try {
        pass1 = extractEvidence(question, rubric, studentAnswer);
      } catch (Exception e) {
        log.warn("Pass 1 (증거 추출) 실패, 1-pass fallback 시도", e);
        return fallbackSinglePass(
            question, modelAnswer, rubric, studentAnswer, attemptCount, startMs);
      }

      // Pass 2: 증거 기반 채점
      PassResult<EssayGradingResult> pass2 =
          gradeWithEvidence(question, modelAnswer, rubric, pass1.result(), attemptCount);

      // 메트릭 합산
      long elapsedMs = System.currentTimeMillis() - startMs;
      long nonCachedInput1 = extractNonCachedInput(pass1.chatResponse());
      long output1 = pass1.chatResponse().getMetadata().getUsage().getCompletionTokens();
      long nonCachedInput2 = extractNonCachedInput(pass2.chatResponse());
      long output2 = pass2.chatResponse().getMetadata().getUsage().getCompletionTokens();

      long totalNonCachedInput = nonCachedInput1 + nonCachedInput2;
      long totalOutput = output1 + output2;
      double totalCost =
          totalNonCachedInput * PRICE_INPUT_PER_1M / 1_000_000
              + totalOutput * PRICE_OUTPUT_PER_1M / 1_000_000;
      metricsRecorder.recordGrading(elapsedMs, totalNonCachedInput, totalOutput, totalCost);

      EssayGradingResult gradingOnly = pass2.result();
      String evJson = serializeEvidence(pass1.result());
      EssayGradingResult result =
          new EssayGradingResult(
              gradingOnly.elementScores(),
              gradingOnly.totalScore(),
              gradingOnly.maxScore(),
              gradingOnly.overallFeedback(),
              evJson);
      log.info(
          "ESSAY 채점 완료 (2-pass): 총점={}/{}, 소요={}ms, 토큰: 입력={}, 출력={}, 비용=${}",
          result.totalScore(),
          result.maxScore(),
          elapsedMs,
          totalNonCachedInput,
          totalOutput,
          String.format("%.6f", totalCost));

      return result;

    } catch (Exception e) {
      metricsRecorder.recordGradingFailure();
      throw new GeminiInfraException("ESSAY 채점 AI 호출 실패", e);
    }
  }

  /** Pass 1: 학생 답안에서 루브릭 요소별 증거를 추출한다. */
  private PassResult<GeminiEvidenceExtractionResponse> extractEvidence(
      String question, String rubric, String studentAnswer) {
    SystemMessage systemMessage = new SystemMessage(EssayEvidenceExtractionGuideLine.get());
    UserMessage userMessage =
        new UserMessage(EssayEvidenceExtractionPrompt.generate(question, rubric, studentAnswer));

    var options =
        GoogleGenAiChatOptions.builder()
            .model(GRADING_MODEL)
            .responseMimeType("application/json")
            .responseSchema(evidenceSchema)
            .build();

    Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
    ChatResponse chatResponse = chatModel.call(prompt);
    String responseText = chatResponse.getResult().getOutput().getText();

    var converter = new BeanOutputConverter<>(GeminiEvidenceExtractionResponse.class);
    GeminiEvidenceExtractionResponse evidence = converter.convert(responseText);

    return new PassResult<>(evidence, chatResponse);
  }

  /** Pass 2: 추출된 증거를 기반으로 채점한다. */
  private PassResult<EssayGradingResult> gradeWithEvidence(
      String question,
      String modelAnswer,
      String rubric,
      GeminiEvidenceExtractionResponse evidence,
      int attemptCount) {
    boolean isFirstAttempt = attemptCount == 1;
    String schema = isFirstAttempt ? firstAttemptSchema : defaultSchema;

    SystemMessage systemMessage = new SystemMessage(EssayGradingGuideLine.of(attemptCount));
    UserMessage userMessage =
        new UserMessage(
            EssayGradingRequestPrompt.generateWithEvidence(
                question, modelAnswer, rubric, evidence, attemptCount));

    var options =
        GoogleGenAiChatOptions.builder()
            .model(GRADING_MODEL)
            .responseMimeType("application/json")
            .responseSchema(schema)
            .build();

    Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
    ChatResponse chatResponse = chatModel.call(prompt);
    String responseText = chatResponse.getResult().getOutput().getText();

    EssayGradingResult result =
        isFirstAttempt ? parseFirstAttempt(responseText) : parseDefault(responseText);

    return new PassResult<>(result, chatResponse);
  }

  /** Pass 1 실패 시 기존 1-pass 방식으로 fallback한다. */
  private EssayGradingResult fallbackSinglePass(
      String question,
      String modelAnswer,
      String rubric,
      String studentAnswer,
      int attemptCount,
      long startMs) {
    boolean isFirstAttempt = attemptCount == 1;
    String schema = isFirstAttempt ? firstAttemptSchema : defaultSchema;

    SystemMessage systemMessage = new SystemMessage(EssayGradingGuideLine.of(attemptCount));
    UserMessage userMessage =
        new UserMessage(
            EssayGradingRequestPrompt.generate(
                question, modelAnswer, rubric, studentAnswer, attemptCount));

    var options =
        GoogleGenAiChatOptions.builder()
            .model(GRADING_MODEL)
            .responseMimeType("application/json")
            .responseSchema(schema)
            .build();

    Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
    ChatResponse chatResponse = chatModel.call(prompt);
    String responseText = chatResponse.getResult().getOutput().getText();

    long elapsedMs = System.currentTimeMillis() - startMs;
    long nonCachedInput = extractNonCachedInput(chatResponse);
    long outputTokens = chatResponse.getMetadata().getUsage().getCompletionTokens();
    double cost =
        nonCachedInput * PRICE_INPUT_PER_1M / 1_000_000
            + outputTokens * PRICE_OUTPUT_PER_1M / 1_000_000;
    metricsRecorder.recordGrading(elapsedMs, nonCachedInput, outputTokens, cost);

    EssayGradingResult result =
        isFirstAttempt ? parseFirstAttempt(responseText) : parseDefault(responseText);

    log.info(
        "ESSAY 채점 완료 (fallback 1-pass): 총점={}/{}, 소요={}ms",
        result.totalScore(),
        result.maxScore(),
        elapsedMs);

    return result;
  }

  private static long extractNonCachedInput(ChatResponse chatResponse) {
    Usage usage = chatResponse.getMetadata().getUsage();
    long inputTokens = usage.getPromptTokens();
    long cachedTokens = 0;
    if (usage instanceof GoogleGenAiUsage g && g.getCachedContentTokenCount() != null) {
      cachedTokens = g.getCachedContentTokenCount();
    }
    return Math.max(0, inputTokens - cachedTokens);
  }

  /** 1차 시도 응답 파싱. feedback 필드 없이 빈 문자열로 채운다. */
  private static EssayGradingResult parseFirstAttempt(String responseText) {
    var converter = new BeanOutputConverter<>(GeminiFirstAttemptGradingResponse.class);
    GeminiFirstAttemptGradingResponse response = converter.convert(responseText);

    List<EssayGradingResult.ElementScore> scores =
        response.elementScores().stream()
            .map(
                e ->
                    new EssayGradingResult.ElementScore(
                        e.element(), e.maxPoints(), e.earnedPoints(), e.level(), ""))
            .toList();

    return new EssayGradingResult(scores, response.totalScore(), response.maxScore(), "", null);
  }

  /** 2차 이후 응답 파싱. 요소별 feedback 포함. */
  private static EssayGradingResult parseDefault(String responseText) {
    var converter = new BeanOutputConverter<>(GeminiGradingResponse.class);
    GeminiGradingResponse response = converter.convert(responseText);

    List<EssayGradingResult.ElementScore> scores =
        response.elementScores().stream()
            .map(
                e ->
                    new EssayGradingResult.ElementScore(
                        e.element(), e.maxPoints(), e.earnedPoints(), e.level(), e.feedback()))
            .toList();

    return new EssayGradingResult(
        scores, response.totalScore(), response.maxScore(), response.overallFeedback(), null);
  }

  /** Pass 1 증거를 JSON 문자열로 직렬화한다. 실패 시 null 반환. */
  private String serializeEvidence(GeminiEvidenceExtractionResponse evidence) {
    try {
      return objectMapper.writeValueAsString(evidence);
    } catch (JsonProcessingException e) {
      log.warn("증거 JSON 직렬화 실패", e);
      return null;
    }
  }
}
