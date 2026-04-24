package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.structure.GeminiEssayQuestion;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 스트리밍 JSON 응답에서 GeminiEssayQuestion 객체가 완성될 때마다 콜백을 호출한다.
 *
 * <p>Gemini 응답 형식: {"questions": [{Q1}, {Q2}, ...]}
 *
 * <p>questions 배열 내부의 각 객체({...})가 완성되면 즉시 파싱하여 consumer에 전달한다.
 */
@Slf4j
public class StreamingEssayQuestionExtractor {

  private final ObjectMapper objectMapper;
  private final Consumer<GeminiEssayQuestion> questionConsumer;

  private final StringBuilder buffer = new StringBuilder();
  private boolean inArray = false;
  private int braceDepth = 0;
  private int objectStart = -1;
  private boolean inString = false;
  private boolean escaped = false;

  @Getter private int questionCount = 0;

  public StreamingEssayQuestionExtractor(
      ObjectMapper objectMapper, Consumer<GeminiEssayQuestion> questionConsumer) {
    this.objectMapper = objectMapper;
    this.questionConsumer = questionConsumer;
  }

  public void feed(String chunk) {
    if (chunk == null) return;

    for (int i = 0; i < chunk.length(); i++) {
      char c = chunk.charAt(i);
      buffer.append(c);

      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\' && inString) {
        escaped = true;
        continue;
      }
      if (c == '"') {
        inString = !inString;
        continue;
      }
      if (inString) continue;

      if (c == '[' && !inArray) {
        inArray = true;
        continue;
      }

      if (!inArray) continue;

      if (c == '{') {
        if (braceDepth == 0) {
          objectStart = buffer.length() - 1;
        }
        braceDepth++;
      } else if (c == '}') {
        braceDepth--;
        if (braceDepth == 0 && objectStart >= 0) {
          String objectJson = buffer.substring(objectStart, buffer.length());
          emitQuestion(objectJson);
          objectStart = -1;
        }
      }
    }
  }

  private void emitQuestion(String json) {
    GeminiEssayQuestion question;
    try {
      question = objectMapper.readValue(json, GeminiEssayQuestion.class);
    } catch (Exception e) {
      log.warn(
          "ESSAY JSON 파싱 실패: {} | JSON 길이: {} | JSON 앞 300자: {}",
          e.getMessage(),
          json.length(),
          json.length() > 300 ? json.substring(0, 300) : json);
      return;
    }
    try {
      questionCount++;
      questionConsumer.accept(question);
    } catch (Exception e) {
      log.warn("ESSAY 문항 처리 실패: {}", e.getMessage(), e);
    }
  }
}
