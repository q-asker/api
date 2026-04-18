package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiQuestion.GeminiSelection;
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

/** 생성된 퀴즈 초안을 1회 LLM 호출로 교정한다. PDF 캐시를 참조하여 원본 강의노트 맥락에서 정보누출을 정확히 감지하고 교정한다. */
@Slf4j
@Component
public class QuizRewriter {

  public record RewriteResult(
      List<GeminiQuestion> questions, long inputTokens, long outputTokens, double cost) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RewrittenQuiz(@JsonPropertyDescription("교정된 문항 목록") List<RewrittenQuestion> questions) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RewrittenQuestion(
      @JsonPropertyDescription("교정된 질문문") String content,
      @JsonPropertyDescription("교정된 선택지 목록 (순서 유지)") List<Object> selections) {

    /** 선택지에서 텍스트만 추출 (String 또는 {content:...} 객체 모두 처리) */
    List<String> selectionTexts() {
      if (selections == null) return List.of();
      return selections.stream()
          .map(
              s -> {
                if (s instanceof String str) return str;
                if (s instanceof java.util.Map<?, ?> map) {
                  Object c = map.get("content");
                  return c != null ? c.toString() : s.toString();
                }
                return s.toString();
              })
          .toList();
    }
  }

  private static final double PRICE_INPUT_PER_1M = 0.10;
  private static final double PRICE_OUTPUT_PER_1M = 0.40;

  private static final String REWRITE_SCHEMA =
      new BeanOutputConverter<>(RewrittenQuiz.class).getJsonSchema();

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;
  private final String model;

  public QuizRewriter(
      ChatModel chatModel, ObjectMapper objectMapper, QAskerAiProperties aiProperties) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
    this.model = aiProperties.getEqualizationModel();
  }

  /**
   * 문항 리스트를 1회 LLM 호출로 교정한다. 선택지 균등화, 오답 매력도, Bloom's 수준을 교정한다.
   *
   * @param questions 교정할 문항 리스트
   * @param language 언어 (KO/EN)
   * @return 교정 결과
   */
  public RewriteResult rewrite(List<GeminiQuestion> questions, String language) {
    if (questions == null || questions.isEmpty()) {
      return new RewriteResult(questions, 0, 0, 0);
    }

    try {
      long startMs = System.currentTimeMillis();
      String userPrompt = buildPrompt(questions, language);

      // 캐시 미참조 — 선택지 균등화/오답 매력도 교정은 강의노트 맥락 불필요
      GoogleGenAiChatOptions.Builder optionsBuilder =
          GoogleGenAiChatOptions.builder().model(model).responseMimeType("application/json");

      ChatResponse chatResponse = chatModel.call(new Prompt(userPrompt, optionsBuilder.build()));
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
      }

      String json = chatResponse.getResult().getOutput().getText();
      if (json == null || json.isBlank()) {
        log.warn("리라이트 응답이 비어있습니다. 원본 유지.");
        return new RewriteResult(questions, inputTokens, outputTokens, cost);
      }

      json =
          json.trim()
              .replaceAll("^\\uFEFF", "")
              .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
      if (json.isBlank()) {
        log.warn("리라이트 응답이 정제 후 비어있습니다. 원본 유지.");
        return new RewriteResult(questions, inputTokens, outputTokens, cost);
      }

      // 모델이 {"questions":[...]} 또는 [...] 형태로 응답할 수 있음
      RewrittenQuiz result;
      json = json.strip();
      if (json.startsWith("[")) {
        List<RewrittenQuestion> items =
            objectMapper.readValue(
                json,
                objectMapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, RewrittenQuestion.class));
        result = new RewrittenQuiz(items);
      } else {
        result = objectMapper.readValue(json, RewrittenQuiz.class);
      }

      if (result.questions() == null || result.questions().isEmpty()) {
        log.warn("리라이트 결과가 비어있습니다. 원본 유지.");
        return new RewriteResult(questions, inputTokens, outputTokens, cost);
      }

      // RewrittenQuestion → GeminiQuestion 변환 (원본의 해설/참조페이지/정답여부 유지)
      List<GeminiQuestion> rewritten = new ArrayList<>();
      for (int idx = 0; idx < result.questions().size() && idx < questions.size(); idx++) {
        RewrittenQuestion rq = result.questions().get(idx);
        GeminiQuestion orig = questions.get(idx);

        List<GeminiSelection> newSelections;
        List<String> rewrittenSels = rq.selectionTexts();
        if (!rewrittenSels.isEmpty()
            && orig.selections() != null
            && rewrittenSels.size() == orig.selections().size()) {
          newSelections = new ArrayList<>();
          for (int si = 0; si < rewrittenSels.size(); si++) {
            GeminiSelection origSel = orig.selections().get(si);
            newSelections.add(
                new GeminiSelection(
                    rewrittenSels.get(si), origSel.correct(), origSel.explanation()));
          }
        } else {
          newSelections = orig.selections();
        }

        rewritten.add(
            new GeminiQuestion(
                rq.content(), newSelections, orig.quizExplanation(), orig.referencedPages()));
      }

      int removed = questions.size() - rewritten.size();
      log.info(
          "리라이트 완료: {}ms, 입력 {}개 → 출력 {}개 (제거 {}개), 비용: ${}",
          elapsedMs,
          questions.size(),
          rewritten.size(),
          removed,
          String.format("%.6f", cost));

      return new RewriteResult(rewritten, inputTokens, outputTokens, cost);
    } catch (Exception e) {
      log.warn("리라이트 실패 (원본 유지): {}", e.getMessage());
      return new RewriteResult(questions, 0, 0, 0);
    }
  }

  private String buildPrompt(List<GeminiQuestion> questions, String language) {
    String questionsText =
        IntStream.range(0, questions.size())
            .mapToObj(
                i -> {
                  GeminiQuestion q = questions.get(i);
                  String selections =
                      q.selections() == null
                          ? ""
                          : IntStream.range(0, q.selections().size())
                              .mapToObj(
                                  j -> "  %d. %s".formatted(j + 1, q.selections().get(j).content()))
                              .collect(Collectors.joining("\n"));
                  int correctIdx =
                      q.selections() == null
                          ? -1
                          : IntStream.range(0, q.selections().size())
                              .filter(j -> q.selections().get(j).correct())
                              .findFirst()
                              .orElse(-1);
                  return "문항 %d:\n질문: %s\n선택지:\n%s\n정답번호: %d\n참조페이지: %s"
                      .formatted(
                          i + 1,
                          q.content(),
                          selections,
                          correctIdx + 1,
                          q.referencedPages() != null ? q.referencedPages().toString() : "[]");
                })
            .collect(Collectors.joining("\n\n---\n\n"));

    return """
        강의노트를 참조하여 아래 퀴즈 문항들의 품질을 교정하세요.

        교정 규칙:
        1. [정보누출 교정] 강의노트를 확인하고, 질문문의 표/인용문에서 정답을 직접 읽을 수 있으면 질문의 관점을 바꿔 여러 정보를 종합해야 답을 도출할 수 있게 만드세요.
        2. [선택지 균등화] 모든 선택지의 글자수를 가장 긴 선택지의 ±20%% 이내로 맞추세요.
        3. [단순기억 상향] 강의노트의 정의를 그대로 묻는 문항은, 2개 이상 개념을 연결하여 판단을 요구하는 질문으로 재구성하세요.
        4. [오답 매력도 교정] 오답에 "항상", "모든", "전혀", "무관하게", "원천적으로", "완전히", "불가능" 같은 절대적 표현이 있으면, 구체적 조건문으로 교체하세요. 예: "항상 발생한다" → "특정 조건 X에서 발생한다"
        5. [기준 명시 확인] 질문문에 **볼드 처리된 판단 기준이 2개 미만**이면, 강의노트에서 상충하는 기준 2개를 찾아 **볼드로** 명시하고 기준 간 트레이드오프를 묻는 형태로 질문을 재구성하세요.
        6. [서식 보존] 원본 질문문에 포함된 마크다운 서식(표, mermaid, 코드블록, 인용문)은 구조 그대로 유지하세요. 서식을 제거하지 마세요.

        유지 규칙:
        - 선택지 순서와 정답 번호는 원본 그대로 유지하세요

        [교정 대상 문항]
        %s"""
        .formatted(questionsText);
  }
}
