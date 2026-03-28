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

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EqualizedSelections(
      @JsonPropertyDescription("균등화된 선택지 텍스트 목록 (순서 유지)") List<String> contents) {}

  private static final String EQUALIZE_SCHEMA =
      new BeanOutputConverter<>(EqualizedSelections.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;

  /**
   * 선택지 content 목록을 의미 보존하면서 길이를 균등화한다. 정답 정보는 전달하지 않는다.
   *
   * @param selectionContents 원본 선택지 텍스트 목록 (4개)
   * @return 균등화된 선택지 텍스트 목록 (순서 유지), 실패 시 null
   */
  public List<String> equalize(List<String> selectionContents) {
    try {
      String userPrompt = buildPrompt(selectionContents);

      Prompt prompt =
          new Prompt(
              userPrompt,
              GoogleGenAiChatOptions.builder()
                  .responseMimeType("application/json")
                  .responseSchema(EQUALIZE_SCHEMA)
                  .build());

      ChatResponse chatResponse = chatModel.call(prompt);
      String json = chatResponse.getResult().getOutput().getText();
      if (json == null || json.isBlank()) {
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

      return result.contents();
    } catch (Exception e) {
      log.warn("선택지 균등화 실패 (원본 유지): {}", e.getMessage());
      return null;
    }
  }

  private String buildPrompt(List<String> contents) {
    StringBuilder sb = new StringBuilder();
    sb.append("다음 4개 서술문의 의미를 보존하면서 길이를 균등하게 다시 작성하세요.\n");
    sb.append("가장 짧은 서술문에 맞춰 나머지를 압축하세요.\n\n");
    for (int i = 0; i < contents.size(); i++) {
      sb.append(i + 1).append(". ").append(contents.get(i)).append("\n");
    }
    return sb.toString();
  }
}
