package com.icc.qasker.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.prompt.user.QuizPlanPrompt;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 문제 생성 전 문항별 출제 계획(페이지 분배 + 마크다운 서식 선택)을 수립한다. 캐시된 PDF를 참조하여 LLM이 직접 계획을 결정하므로, 내용에 적합한 서식이 선택된다.
 */
@Slf4j
@Component
public class QuizPlannerService {

  /** 계획 결과: 문항별 계획 + 토큰/비용 */
  public record PlanResult(
      List<QuizPlanItem> items, long inputTokens, long outputTokens, double cost) {}

  /** 문항별 계획 항목 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record QuizPlanItem(
      @JsonPropertyDescription("사용할 마크다운 서식") String format,
      @JsonPropertyDescription("마크다운 활용 방안") String formatUsage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record QuizPlanResponse(@JsonPropertyDescription("문항별 출제 계획 목록") List<QuizPlanItem> items) {}

  private static final double PRICE_INPUT_PER_1M = 0.10;
  private static final double PRICE_OUTPUT_PER_1M = 0.40;

  private static final String PLAN_SCHEMA =
      new BeanOutputConverter<>(QuizPlanResponse.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;

  public QuizPlannerService(ChatModel chatModel, ObjectMapper objectMapper) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
  }

  /**
   * 캐시된 PDF를 참조하여 문항별 마크다운 서식을 결정한다.
   *
   * @param chunks 청크 목록 (참조 페이지 + 문제 수)
   * @param cacheName Gemini Cached Content 이름
   * @param language 언어 (KO, EN)
   * @return 계획 결과, 실패 시 null
   */
  public PlanResult plan(List<ChunkInfo> chunks, String cacheName, String language) {
    int quizCount = chunks.stream().mapToInt(ChunkInfo::quizCount).sum();
    try {
      long startMs = System.currentTimeMillis();
      String userPrompt = QuizPlanPrompt.generate(chunks, language);

      GoogleGenAiChatOptions options =
          GoogleGenAiChatOptions.builder()
              .useCachedContent(true)
              .cachedContentName(cacheName)
              .responseMimeType("application/json")
              .responseSchema(PLAN_SCHEMA)
              .build();
      Prompt prompt = new Prompt(userPrompt, options);

      ChatResponse chatResponse = chatModel.call(prompt);
      long elapsedMs = System.currentTimeMillis() - startMs;

      double cost = 0.0;
      long inputTokens = 0;
      long outputTokens = 0;
      if (chatResponse.getMetadata().getUsage() != null) {
        var usage = chatResponse.getMetadata().getUsage();
        inputTokens = usage.getPromptTokens();
        outputTokens = usage.getCompletionTokens();
        cost =
            inputTokens * PRICE_INPUT_PER_1M / 1_000_000
                + outputTokens * PRICE_OUTPUT_PER_1M / 1_000_000;
        log.info(
            "문항 계획 완료 - {}ms, 토큰: 입력={}, 출력={}, 추정 비용: ${}",
            elapsedMs,
            inputTokens,
            outputTokens,
            String.format("%.6f", cost));
      }

      String json = chatResponse.getResult().getOutput().getText();
      if (json == null || json.isBlank()) {
        log.warn("문항 계획 응답이 비어있습니다.");
        return null;
      }
      QuizPlanResponse result = objectMapper.readValue(json.trim(), QuizPlanResponse.class);

      if (result.items() == null) {
        log.warn("문항 계획 응답 items가 null입니다.");
        return null;
      }

      List<QuizPlanItem> items = result.items();
      if (items.size() != quizCount) {
        log.info("문항 계획 개수 조정: expected={}, actual={}", quizCount, items.size());
        if (items.size() > quizCount) {
          items = items.subList(0, quizCount);
        }
        // items < quizCount인 경우 부족분은 buildChunkPlanExtras에서 null로 처리
      }

      for (int i = 0; i < items.size(); i++) {
        QuizPlanItem item = items.get(i);
        log.info("문항[{}] 계획: format={}, usage={}", i + 1, item.format(), item.formatUsage());
      }

      return new PlanResult(items, inputTokens, outputTokens, cost);
    } catch (Exception e) {
      log.warn("문항 계획 실패 (기존 파이프라인으로 폴백): {}", e.getMessage());
      return null;
    }
  }
}
