package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiQuestion.GeminiSelection;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 2-Stage 파이프라인의 Stage 2: 포맷팅. 생성된 퀴즈의 마크다운 서식 적용 + 선택지 길이 균등화를 담당한다. correct 필드 없이 content만 전달하여
 * 모델의 정답 편향을 차단한다.
 */
@Slf4j
@Component
public class FormattingPipeline {

  /** 포맷팅 응답 스키마 — correct 필드 없이 content/explanation만 포함 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FormattedResponse(
      @JsonPropertyDescription("포맷팅된 문항 목록") List<FormattedQuestion> questions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FormattedQuestion(
        @JsonPropertyDescription("마크다운 포맷팅된 문항 줄기") String content,
        @JsonPropertyDescription("길이 균등화 + 마크다운 포맷팅된 선택지 텍스트 목록") List<String> selections,
        @JsonPropertyDescription("마크다운 포맷팅된 선택지별 해설 목록") List<String> explanations,
        @JsonPropertyDescription("마크다운 포맷팅된 문항 전체 해설") String quizExplanation) {}
  }

  private static final String FORMATTING_SCHEMA =
      new BeanOutputConverter<>(FormattedResponse.class).getJsonSchema();

  private static final String FORMATTING_PROMPT =
      """
      다음 퀴즈의 모든 텍스트를 마크다운으로 포맷팅하고, 선택지 길이를 균등하게 조정하세요.

      **포맷팅 규칙**:
      - 비교·분류·대응 관계가 있으면 마크다운 테이블로 정리하세요 (2열 이상, 헤더 행, 구분선 포함)
      - 코드 스니펫은 코드블록(```)으로, 인라인 코드는 백틱(`)으로 표기하세요
      - **굵게**, 목록(-), > 인용 등 서식을 활용하여 가독성을 높이세요
      - \\n 줄바꿈으로 논리적 단락을 구분하세요

      **선택지 균등화 규칙**:
      - 모든 선택지의 의미를 보존하면서 길이를 균등하게 조정하세요
      - 가장 짧은 선택지에 맞춰 나머지를 압축하세요

      **순서와 개수를 반드시 유지하세요.**

      [원본 데이터]
      """;

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final String formattingModel;

  public FormattingPipeline(
      ChatModel chatModel, ObjectMapper objectMapper, QAskerAiProperties aiProperties) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.formattingModel = aiProperties.getFormattingModel();
  }

  /**
   * 생성된 문항 목록을 포맷팅한다. correct 필드를 제거하여 정답 편향을 차단하고, 마크다운 서식 적용 + 선택지 길이 균등화를 수행한다.
   *
   * @param questions 원본 문항 목록 (Stage 1 출력)
   * @return 포맷팅된 문항 목록 (correct/explanation 원본 유지), 실패 시 원본 반환
   */
  public List<GeminiQuestion> format(List<GeminiQuestion> questions) {
    try {
      String inputJson = buildInputJson(questions);
      String userPrompt = FORMATTING_PROMPT + inputJson;

      GoogleGenAiChatOptions.Builder optionsBuilder =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(FORMATTING_SCHEMA);

      if (formattingModel != null && !formattingModel.isBlank()) {
        optionsBuilder.model(formattingModel);
      }

      Prompt prompt = new Prompt(userPrompt, optionsBuilder.build());
      ChatResponse chatResponse = chatModel.call(prompt);
      String json = chatResponse.getResult().getOutput().getText();

      if (json == null || json.isBlank()) {
        log.warn("포맷팅 응답이 비어있습니다. 원본을 사용합니다.");
        return questions;
      }

      FormattedResponse formatted = objectMapper.readValue(json.trim(), FormattedResponse.class);

      if (formatted.questions() == null || formatted.questions().size() != questions.size()) {
        log.warn(
            "포맷팅 문항 수 불일치: expected={}, actual={}. 원본을 사용합니다.",
            questions.size(),
            formatted.questions() == null ? 0 : formatted.questions().size());
        return questions;
      }

      return mergeFormatted(questions, formatted);
    } catch (Exception e) {
      log.warn("포맷팅 파이프라인 실패 (원본 유지): {}", e.getMessage());
      return questions;
    }
  }

  /** correct 필드를 제거한 입력 JSON을 생성한다. */
  private String buildInputJson(List<GeminiQuestion> questions) throws Exception {
    List<Object> stripped = new ArrayList<>();
    for (GeminiQuestion q : questions) {
      List<String> selContents =
          q.selections() != null
              ? q.selections().stream().map(GeminiSelection::content).toList()
              : List.of();
      List<String> selExplanations =
          q.selections() != null
              ? q.selections().stream().map(GeminiSelection::explanation).toList()
              : List.of();

      stripped.add(
          new FormattedResponse.FormattedQuestion(
              q.content(), selContents, selExplanations, q.quizExplanation()));
    }
    return objectMapper.writeValueAsString(java.util.Map.of("questions", stripped));
  }

  /** 포맷팅 결과를 원본의 correct 필드와 병합한다. */
  private List<GeminiQuestion> mergeFormatted(
      List<GeminiQuestion> originals, FormattedResponse formatted) {
    List<GeminiQuestion> result = new ArrayList<>();
    for (int i = 0; i < originals.size(); i++) {
      GeminiQuestion orig = originals.get(i);
      FormattedResponse.FormattedQuestion fmt = formatted.questions().get(i);

      // 선택지 수가 다르면 원본 사용
      if (fmt.selections() == null || fmt.selections().size() != orig.selections().size()) {
        result.add(orig);
        continue;
      }

      List<GeminiSelection> newSels = new ArrayList<>();
      for (int j = 0; j < orig.selections().size(); j++) {
        GeminiSelection origSel = orig.selections().get(j);
        String fmtContent = fmt.selections().get(j);
        String fmtExplanation =
            (fmt.explanations() != null && j < fmt.explanations().size())
                ? fmt.explanations().get(j)
                : origSel.explanation();
        newSels.add(new GeminiSelection(fmtContent, origSel.correct(), fmtExplanation));
      }

      result.add(
          new GeminiQuestion(
              fmt.content() != null ? fmt.content() : orig.content(),
              newSels,
              fmt.quizExplanation() != null ? fmt.quizExplanation() : orig.quizExplanation()));
    }
    return result;
  }
}
