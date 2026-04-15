package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.prompt.user.EqualizationPrompt;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import java.util.List;
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
public class SelectionEqualizer {

  /** 균등화 결과: 균등화된 텍스트 + 토큰/비용 */
  public record EqualizeResult(
      List<String> contents, long inputTokens, long outputTokens, double cost) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EqualizedSelections(
      @JsonPropertyDescription("균등화된 선택지 텍스트 목록 (순서 유지)") List<String> contents) {}

  // Gemini 2.5 Flash Lite 단가 (equalization-model 기준)
  private static final double PRICE_INPUT_PER_1M = 0.10;
  private static final double PRICE_OUTPUT_PER_1M = 0.40;

  private static final String EQUALIZE_SCHEMA =
      new BeanOutputConverter<>(EqualizedSelections.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final String equalizationModel;

  public SelectionEqualizer(
      ChatModel chatModel, ObjectMapper objectMapper, QAskerAiProperties aiProperties) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.equalizationModel = aiProperties.getEqualizationModel();
  }

  /**
   * 오답 선택지만 정답 길이에 맞춰 균등화한다. 정답은 전달하지 않아 편향을 원천 차단한다.
   *
   * @param wrongContents 오답 선택지 텍스트 목록 (3개)
   * @param correctLength 정답 선택지의 글자 수 (목표치)
   * @return 균등화 결과 (텍스트 + 비용), 실패 시 null
   */
  public EqualizeResult equalize(List<String> wrongContents, int correctLength, String language) {
    try {
      int targetLength = correctLength;

      long startMs = System.currentTimeMillis();
      String userPrompt = EqualizationPrompt.generate(wrongContents, targetLength, language);

      GoogleGenAiChatOptions.Builder optionsBuilder =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(EQUALIZE_SCHEMA);
      if (equalizationModel != null && !equalizationModel.isBlank()) {
        optionsBuilder.model(equalizationModel);
      }
      Prompt prompt = new Prompt(userPrompt, optionsBuilder.build());

      ChatResponse chatResponse = chatModel.call(prompt);
      long elapsedMs = System.currentTimeMillis() - startMs;

      // 토큰/비용 계산
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

      if (result.contents() == null || result.contents().size() != wrongContents.size()) {
        log.warn(
            "균등화 응답 개수 불일치: expected={}, actual={}",
            wrongContents.size(),
            result.contents() == null ? 0 : result.contents().size());
        return null;
      }

      // 변경 전/후 선택지 비교
      for (int i = 0; i < wrongContents.size(); i++) {
        String before = wrongContents.get(i);
        String after = result.contents().get(i);
        log.info(
            "선택지[{}] 변경 전({}자): {} → 변경 후({}자): {}",
            i + 1,
            before.length(),
            before,
            after.length(),
            after);
      }

      return new EqualizeResult(result.contents(), inputTokens, outputTokens, cost);
    } catch (Exception e) {
      log.warn("선택지 균등화 실패 (원본 유지): {}", e.getMessage());
      return null;
    }
  }
}
