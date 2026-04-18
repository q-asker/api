package com.icc.qasker.ai.service.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.prompt.QuizType;
import com.icc.qasker.ai.structure.GeminiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;

/**
 * Gemini ChatModel 호출, JSON 응답 파싱, 메트릭 기록을 담당한다. QuizOrchestrationServiceImpl에서 Chat 관심사를 분리하여
 * 오케스트레이터가 흐름 제어에만 집중할 수 있게 한다.
 */
@Slf4j
@Component
public class GeminiChatService {

  /** 청크 처리 결과: 파싱된 응답 + 추정 비용 */
  public record ParsedResult(GeminiResponse response, double cost) {}

  private static final String RESPONSE_JSON_SCHEMA =
      new BeanOutputConverter<>(GeminiResponse.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final GeminiMetricsRecorder metricsRecorder;

  public GeminiChatService(
      ChatModel chatModel, ObjectMapper objectMapper, GeminiMetricsRecorder metricsRecorder) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
  }

  /**
   * 캐시된 컨텍스트를 사용하여 Gemini API를 호출하고, 파싱된 결과를 반환한다.
   *
   * @param chunk 청크 정보 (참조 페이지, 문제 수)
   * @param cacheName Gemini Cached Content 이름
   * @param strategyValue 퀴즈 타입 (MULTIPLE, OX, BLANK)
   * @param planExtra 문항 계획 결과 (nullable, MULTIPLE만 사용)
   * @return 파싱 결과 (응답 + 비용), 응답이 비어있으면 null
   */
  public ParsedResult callAndParse(
      ChunkInfo chunk, String cacheName, String strategyValue, String language, String planExtra)
      throws Exception {
    long startMs = System.currentTimeMillis();
    List<Integer> pages = chunk.referencedPages();
    QuizType quizType = QuizType.valueOf(strategyValue);

    String userPrompt = quizType.generateRequestPrompt(pages, chunk.quizCount(), planExtra);

    // 캐시/JSON 설정만 Prompt-level로 전달. model, temperature, thinkingLevel 등은
    // yml defaultOptions에서 merge로 적용됨 (Prompt-level에 설정하면 copyToTarget에서 손실)
    Prompt prompt =
        new Prompt(
            userPrompt,
            GoogleGenAiChatOptions.builder()
                .useCachedContent(true)
                .cachedContentName(cacheName)
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_JSON_SCHEMA)
                .build());

    ChatResponse chatResponse = chatModel.call(prompt);
    long elapsedMs = System.currentTimeMillis() - startMs;

    // Prometheus 메트릭 기록 + 비용 추정
    double chunkCost = 0.0;
    if (chatResponse.getMetadata().getUsage() != null) {
      var usage = chatResponse.getMetadata().getUsage();
      chunkCost = metricsRecorder.recordChunkResult(elapsedMs, usage);
      long thinkingTokens =
          usage instanceof GoogleGenAiUsage g && g.getThoughtsTokenCount() != null
              ? g.getThoughtsTokenCount()
              : 0;
      log.info(
          "Gemini Usage - pages={}, {}ms, 토큰: 입력={}(캐시: {}), 추론={}, 출력={}, 추정 비용: ${}",
          pages,
          elapsedMs,
          usage.getPromptTokens(),
          usage instanceof GoogleGenAiUsage g2
              ? g2.getCachedContentTokenCount()
              : Integer.valueOf(0),
          thinkingTokens,
          usage.getCompletionTokens(),
          String.format("%.6f", chunkCost));
    }

    String json = chatResponse.getResult().getOutput().getText();
    if (json == null || json.isBlank()) {
      log.error("응답이 비어있습니다: pages={}", pages);
      return new ParsedResult(null, chunkCost);
    }

    return new ParsedResult(objectMapper.readValue(json.trim(), GeminiResponse.class), chunkCost);
  }
}
