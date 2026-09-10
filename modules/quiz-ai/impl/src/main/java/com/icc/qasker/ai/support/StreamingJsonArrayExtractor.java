package com.icc.qasker.ai.support;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class StreamingJsonArrayExtractor<T> {

  private final ObjectMapper objectMapper;
  private final Class<T> elementType;
  private final String logTag;

  private final StringBuilder buffer = new StringBuilder();
  private boolean inArray = false;
  private int braceDepth = 0;
  private int objectStart = -1;
  private boolean inString = false;
  private boolean escaped = false;

  @Getter private int questionCount = 0;

  public StreamingJsonArrayExtractor(
      ObjectMapper objectMapper, Class<T> elementType, String logTag) {
    this.objectMapper = objectMapper;
    this.elementType = elementType;
    this.logTag = logTag;
  }

  public List<T> feed(String chunk) {
    if (chunk == null) return List.of();

    List<T> completed = new ArrayList<>();

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

      // 배열 시작 감지
      if (c == '[' && !inArray) {
        inArray = true;
        continue;
      }

      if (!inArray) continue;

      // 원소 객체 추적
      if (c == '{') {
        if (braceDepth == 0) {
          objectStart = buffer.length() - 1;
        }
        braceDepth++;
      } else if (c == '}') {
        braceDepth--;
        if (braceDepth == 0 && objectStart >= 0) {
          String objectJson = buffer.substring(objectStart, buffer.length());
          T element = parseElement(objectJson);
          if (element != null) completed.add(element);
          objectStart = -1;
        }
      }
    }
    return completed;
  }

  private T parseElement(String json) {
    T element;
    try {
      element = objectMapper.readValue(json, elementType);
    } catch (Exception e) {
      log.warn(
          "[{} JSON 파싱 실패] 문항 JSON 역직렬화 실패 jsonLength={} jsonPreview={}",
          logTag,
          json.length(),
          json.length() > 300 ? json.substring(0, 300) : json,
          e);
      return null;
    }
    questionCount++;
    return element;
  }
}
