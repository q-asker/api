package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** 선택지 길이 균등화를 담당한다. 정답/오답 정보 없이 content만 전달하여 모델의 정답 편향을 차단한 상태로 길이를 균등화한다. */
@Slf4j
@Component
@AllArgsConstructor
public class SelectionEqualizer {

  /** 균등화 결과: 균등화된 텍스트 + 추정 비용 */
  public record EqualizeResult(List<String> contents, double cost) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EqualizedSelections(
      @JsonPropertyDescription("균등화된 선택지 텍스트 목록 (순서 유지)") List<String> contents) {}

  // Gemini 2.5 Flash 단가
  private static final double PRICE_INPUT_PER_1M = 0.30;
  private static final double PRICE_OUTPUT_PER_1M = 2.50;

  private static final String EQUALIZE_SCHEMA =
      new BeanOutputConverter<>(EqualizedSelections.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;

  /**
   * 선택지 content 목록을 의미 보존하면서 길이를 균등화한다. 정답 정보는 전달하지 않는다.
   *
   * @param selectionContents 원본 선택지 텍스트 목록 (4개)
   * @return 균등화 결과 (텍스트 + 비용), 실패 시 null
   */
  public EqualizeResult equalize(List<String> selectionContents) {
    try {
      long startMs = System.currentTimeMillis();
      String userPrompt = buildPrompt(selectionContents);

      Prompt prompt =
          new Prompt(
              userPrompt,
              GoogleGenAiChatOptions.builder()
                  .responseMimeType("application/json")
                  .responseSchema(EQUALIZE_SCHEMA)
                  .build());

      ChatResponse chatResponse = chatModel.call(prompt);
      long elapsedMs = System.currentTimeMillis() - startMs;

      // 비용 계산
      double cost = 0.0;
      if (chatResponse.getMetadata().getUsage() != null) {
        var usage = chatResponse.getMetadata().getUsage();
        long inputTokens = usage.getPromptTokens();
        long outputTokens = usage.getCompletionTokens();
        cost =
            inputTokens * PRICE_INPUT_PER_1M / 1_000_000
                + outputTokens * PRICE_OUTPUT_PER_1M / 1_000_000;
        log.info(
            "선택지 균등화 완료 - {}ms, 토큰: 입력={}, 출력={}, 추정 비용: ${}",
            elapsedMs,
            inputTokens,
            outputTokens,
            String.format("%.6f", cost));
      }

      String json = chatResponse.getResult().getOutput().getText();
      if (json == null || json.isBlank()) {
        log.warn("선택지 균등화 응답이 비어있습니다.");
        return null;
      }

      EqualizedSelections result = objectMapper.readValue(json.trim(), EqualizedSelections.class);

      if (result.contents() == null || result.contents().size() != selectionContents.size()) {
        log.warn(
            "균등화 응답 개수 불일치: expected={}, actual={}",
            selectionContents.size(),
            result.contents() == null ? 0 : result.contents().size());
        return null;
      }

      return new EqualizeResult(result.contents(), cost);
    } catch (Exception e) {
      log.warn("선택지 균등화 실패 (원본 유지): {}", e.getMessage());
      return null;
    }
  }

  private String buildPrompt(List<String> contents) {
    StringBuilder sb = new StringBuilder();
    sb.append("다음 4개 서술문의 길이를 균등하게 맞추세요.\n");
    sb.append("가장 긴 서술문은 그대로 유지하고, 짧은 서술문을 가장 긴 것과 비슷한 길이로 늘려 작성하세요.\n");
    sb.append("각 서술문의 주장과 결론을 변경하지 마세요. 수식어나 부연 설명만 추가하세요.\n\n");
    for (int i = 0; i < contents.size(); i++) {
      sb.append(i + 1).append(". ").append(contents.get(i)).append("\n");
    }
    return sb.toString();
  }
}
