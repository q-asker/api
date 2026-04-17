package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.structure.GeminiQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** LLM 기반 문항 품질 검증기. Gemini Flash Lite를 사용하여 정보누출과 주제중복을 감지한다. 청크당 1회 API 호출, ~2-5초 지연. */
@Slf4j
@Component
public class QuizQualityValidator {

  /** 검증 결과 */
  public record ValidationResult(
      List<GeminiQuestion> passed,
      int filtered,
      long inputTokens,
      long outputTokens,
      double cost) {}

  /** LLM 응답 스키마: 각 문항의 통과 여부 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record QualityVerdict(
      @JsonPropertyDescription("각 문항의 통과 여부 (true=통과, false=제거)") List<Boolean> pass) {}

  private static final double PRICE_INPUT_PER_1M = 0.10;
  private static final double PRICE_OUTPUT_PER_1M = 0.40;

  private static final String VERDICT_SCHEMA =
      new BeanOutputConverter<>(QualityVerdict.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final String equalizationModel;

  public QuizQualityValidator(
      ChatModel chatModel, ObjectMapper objectMapper, QAskerAiProperties aiProperties) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.equalizationModel = aiProperties.getEqualizationModel();
  }

  /**
   * 문항 리스트를 LLM으로 검증하여 품질 기준을 통과한 문항만 반환한다.
   *
   * @param questions 검증할 문항 리스트
   * @param previousTopics 이전 청크에서 이미 출제된 주제 요약 (null 가능)
   * @return 검증 결과 (통과 문항 + 비용)
   */
  public ValidationResult validate(List<GeminiQuestion> questions, String previousTopics) {
    if (questions == null || questions.isEmpty()) {
      return new ValidationResult(questions, 0, 0, 0, 0);
    }

    try {
      long startMs = System.currentTimeMillis();

      String prompt = buildPrompt(questions, previousTopics);

      GoogleGenAiChatOptions.Builder optionsBuilder =
          GoogleGenAiChatOptions.builder()
              .responseMimeType("application/json")
              .responseSchema(VERDICT_SCHEMA);
      if (equalizationModel != null && !equalizationModel.isBlank()) {
        optionsBuilder.model(equalizationModel);
      }

      ChatResponse chatResponse = chatModel.call(new Prompt(prompt, optionsBuilder.build()));
      long elapsedMs = System.currentTimeMillis() - startMs;

      // 비용 계산
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
      }

      String json = chatResponse.getResult().getOutput().getText();
      if (json == null || json.isBlank()) {
        log.warn("품질 검증 응답이 비어있습니다. 원본 유지.");
        return new ValidationResult(questions, 0, inputTokens, outputTokens, cost);
      }

      QualityVerdict verdict = objectMapper.readValue(json.trim(), QualityVerdict.class);

      if (verdict.pass() == null || verdict.pass().size() != questions.size()) {
        log.warn(
            "품질 검증 응답 개수 불일치: expected={}, actual={}. 원본 유지.",
            questions.size(),
            verdict.pass() == null ? 0 : verdict.pass().size());
        return new ValidationResult(questions, 0, inputTokens, outputTokens, cost);
      }

      // 통과/제거 분리
      List<GeminiQuestion> passed = new ArrayList<>();
      int filtered = 0;
      for (int i = 0; i < questions.size(); i++) {
        if (Boolean.TRUE.equals(verdict.pass().get(i))) {
          passed.add(questions.get(i));
        } else {
          filtered++;
          log.info("LLM 품질 검증 제거: \"{}\"", truncate(questions.get(i).content(), 60));
        }
      }

      log.info(
          "LLM 품질 검증 완료: {}ms, 입력 {}개 → 통과 {}개, 제거 {}개, 비용: ${}",
          elapsedMs,
          questions.size(),
          passed.size(),
          filtered,
          String.format("%.6f", cost));

      return new ValidationResult(passed, filtered, inputTokens, outputTokens, cost);
    } catch (Exception e) {
      log.warn("LLM 품질 검증 실패 (원본 유지): {}", e.getMessage());
      return new ValidationResult(questions, 0, 0, 0, 0);
    }
  }

  private String buildPrompt(List<GeminiQuestion> questions, String previousTopics) {
    String questionsText =
        IntStream.range(0, questions.size())
            .mapToObj(
                i -> {
                  GeminiQuestion q = questions.get(i);
                  String correctAnswer =
                      q.selections() == null
                          ? ""
                          : q.selections().stream()
                              .filter(GeminiQuestion.GeminiSelection::correct)
                              .map(GeminiQuestion.GeminiSelection::content)
                              .findFirst()
                              .orElse("");
                  return "문항 %d:\n질문: %s\n정답: %s"
                      .formatted(i + 1, truncate(q.content(), 300), truncate(correctAnswer, 200));
                })
            .collect(Collectors.joining("\n\n"));

    String previousContext =
        (previousTopics != null && !previousTopics.isBlank())
            ? "\n\n[이미 출제된 주제]\n" + previousTopics
            : "";

    return """
        다음 문항들의 품질을 검증하세요. 각 문항에 대해 pass=true(통과) 또는 pass=false(제거)를 판정하세요.

        제거 기준 — 아래 조건에 **명백하게** 해당하는 경우에만 false로 판정하세요. 애매하면 true(통과)로 판정하세요:
        1. 질문문에 포함된 표/인용문에서 정답의 핵심 내용을 그대로 읽을 수 있는 경우 (정답이 자료에 직접 서술됨)
        2. 이미 출제된 문항과 질문 의도와 정답이 거의 동일한 경우 (단순히 같은 주제를 다루는 것은 중복이 아님. 관점이 다르면 통과)

        판정 원칙:
        - 같은 기술(예: Rebalancing)을 다루더라도 질문 관점이 다르면 통과
        - 표/다이어그램이 포함되어 있어도 추론이 필요하면 통과
        - 의심스러우면 통과 (false positive보다 false negative가 나음)

        %s

        [검증 대상 문항]
        %s"""
        .formatted(previousContext, questionsText);
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
