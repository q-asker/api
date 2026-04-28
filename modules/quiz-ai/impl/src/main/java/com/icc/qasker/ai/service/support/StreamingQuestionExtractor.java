package com.icc.qasker.ai.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.structure.GeminiQuestion;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 스트리밍 JSON 응답에서 GeminiQuestion 객체가 완성될 때마다 콜백을 호출한다.
 *
 * <p>Gemini 응답 형식: {"questions": [{Q1}, {Q2}, ...]}
 *
 * <p>questions 배열 내부의 각 객체({...})가 완성되면 즉시 파싱하여 consumer에 전달한다. 문자열 내부의 중괄호를 무시하기 위해 JSON 문자열
 * 이스케이프를 추적한다.
 */
@Slf4j
public class StreamingQuestionExtractor {

  private final ObjectMapper objectMapper;
  private final Consumer<GeminiQuestion> questionConsumer;

  private final StringBuilder buffer = new StringBuilder();
  private boolean inArray = false;
  private int braceDepth = 0;
  private int objectStart = -1;
  private boolean inString = false;
  private boolean escaped = false;

  /** -- GETTER -- 추출된 문항 수를 반환한다. */
  @Getter private int questionCount = 0;

  public StreamingQuestionExtractor(
      ObjectMapper objectMapper, Consumer<GeminiQuestion> questionConsumer) {
    this.objectMapper = objectMapper;
    this.questionConsumer = questionConsumer;
  }

  /** 스트리밍 텍스트 청크를 받아 처리한다. 문항 객체가 완성되면 consumer가 호출된다. */
  public void feed(String chunk) {
    if (chunk == null) return;

    for (int i = 0; i < chunk.length(); i++) {
      char c = chunk.charAt(i);
      buffer.append(c);

      // JSON 문자열 내부에서는 중괄호를 무시
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

      // questions 배열 시작 감지
      if (c == '[' && !inArray) {
        inArray = true;
        continue;
      }

      if (!inArray) continue;

      // 문항 객체 추적
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
    GeminiQuestion question;
    try {
      question = objectMapper.readValue(json, GeminiQuestion.class);
    } catch (Exception e) {
      log.warn(
          "[JSON 파싱 실패] 문항 JSON 역직렬화 실패 jsonLength={} jsonPreview={}",
          json.length(),
          json.length() > 300 ? json.substring(0, 300) : json,
          e);
      return;
    }
    try {
      questionCount++;
      questionConsumer.accept(question);
    } catch (Exception e) {
      log.warn("[문항 처리 실패] 문항 소비자 호출 중 오류 발생", e);
    }
  }
}
