package com.icc.qasker.ai.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.ai.structure.GeminiQuestionEntry;
import com.icc.qasker.ai.structure.GeminiSplitResponse;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Gemini 스트리밍 응답에서 JSON 청크를 누적하고, questions 배열 완료 시점과 전체 응답 완료 시점을 감지한다.
 *
 * <p>Gemini는 스키마 키 순서대로 생성하므로 questions가 먼저 완료된다. questions 배열의 닫는 bracket(])을 감지하면 즉시 questions를
 * 파싱하여 콜백을 호출하고, 스트림 종료 시 전체 JSON을 파싱하여 explanations를 포함한 콜백을 호출한다.
 */
@Slf4j
public class StreamingJsonSplitParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String QUESTIONS_KEY = "\"questions\"";

  private final Consumer<List<GeminiQuestionEntry>> onQuestionsReady;
  private final Consumer<GeminiSplitResponse> onFullResponseReady;
  private final Consumer<Exception> onError;

  private final StringBuilder buffer = new StringBuilder();
  private boolean questionsEmitted = false;

  // questions 배열 감지 상태
  private boolean questionsKeyFound = false;
  private boolean questionsArrayStarted = false;
  private int bracketDepth = 0;
  private int questionsArrayEndIndex = -1;

  // JSON 문자열 내부 bracket 무시를 위한 상태
  private boolean inString = false;
  private boolean escaped = false;

  public StreamingJsonSplitParser(
      Consumer<List<GeminiQuestionEntry>> onQuestionsReady,
      Consumer<GeminiSplitResponse> onFullResponseReady,
      Consumer<Exception> onError) {
    this.onQuestionsReady = onQuestionsReady;
    this.onFullResponseReady = onFullResponseReady;
    this.onError = onError;
  }

  /** 스트리밍 청크를 수신하여 누적하고, questions 배열 완료를 감지한다. */
  public void onChunk(String textChunk) {
    if (textChunk == null || textChunk.isEmpty()) {
      return;
    }

    int startIndex = buffer.length();
    buffer.append(textChunk);

    if (!questionsEmitted) {
      scanForQuestionsComplete(startIndex);
    }
  }

  /** 스트림 종료 시 전체 JSON을 파싱한다. */
  public void onStreamComplete() {
    String json = buffer.toString().trim();
    if (json.isEmpty()) {
      onError.accept(new IllegalStateException("스트리밍 응답이 비어있습니다"));
      return;
    }

    // questions가 아직 미전송이면 전체 파싱에서 추출하여 전송
    try {
      GeminiSplitResponse response = OBJECT_MAPPER.readValue(json, GeminiSplitResponse.class);

      if (!questionsEmitted && response.questions() != null) {
        questionsEmitted = true;
        onQuestionsReady.accept(response.questions());
      }

      onFullResponseReady.accept(response);
    } catch (JsonProcessingException e) {
      log.error("전체 응답 JSON 파싱 실패: {}", e.getMessage());
      // questions까지는 수신했다면 부분 성공으로 처리
      if (questionsEmitted) {
        onError.accept(new PartialResponseException("해설 파싱 실패 — 문제는 이미 전송됨", e));
      } else {
        onError.accept(e);
      }
    }
  }

  /** 스트림 에러 시 가능한 범위까지 부분 파싱을 시도한다. */
  public void onStreamError(Exception ex) {
    log.error("스트리밍 에러 발생: {}", ex.getMessage());

    // questions가 이미 전송되었으면 해설만 FAILED
    if (questionsEmitted) {
      onError.accept(new PartialResponseException("스트리밍 중단 — 문제는 이미 전송됨", ex));
      return;
    }

    // questions 미전송이면 부분 파싱 시도
    if (questionsArrayEndIndex > 0) {
      tryParsePartialQuestions();
    } else {
      onError.accept(ex);
    }
  }

  /**
   * 버퍼에서 questions 배열 완료를 감지한다.
   *
   * <p>JSON 문자열 내부의 bracket을 무시하기 위해 인용부호 상태를 추적한다. questions 키를 찾은 후 첫 번째 '['부터 bracket depth를
   * 추적하여, depth가 0으로 돌아오면 배열 완료로 판단한다.
   */
  private void scanForQuestionsComplete(int fromIndex) {
    String text = buffer.toString();

    for (int i = Math.max(fromIndex, 0); i < text.length(); i++) {
      char c = text.charAt(i);

      // 이스케이프 처리
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }

      // 문자열 내부/외부 전환
      if (c == '"') {
        inString = !inString;

        // questions 키 탐색 (문자열 외부에서 키를 찾으면 안 되므로, 키 이전 상태에서만 탐색)
        if (!questionsKeyFound && !inString) {
          // 방금 닫힌 문자열이 "questions"인지 확인
          int keyStart = text.lastIndexOf(QUESTIONS_KEY, i);
          if (keyStart >= 0 && keyStart + QUESTIONS_KEY.length() - 1 == i) {
            questionsKeyFound = true;
          }
        }
        continue;
      }

      // 문자열 내부면 bracket 무시
      if (inString) {
        continue;
      }

      // questions 키를 찾은 후 bracket 추적
      if (questionsKeyFound) {
        if (c == '[') {
          if (!questionsArrayStarted) {
            questionsArrayStarted = true;
          }
          bracketDepth++;
        } else if (c == ']') {
          bracketDepth--;
          if (questionsArrayStarted && bracketDepth == 0) {
            questionsArrayEndIndex = i;
            emitQuestions();
            return;
          }
        }
      }
    }
  }

  /** questions 배열을 파싱하여 콜백을 호출한다. */
  private void emitQuestions() {
    String text = buffer.toString();
    // "questions" 키의 ':' 이후부터 ']'까지 추출
    int colonIndex = text.indexOf(':', text.indexOf(QUESTIONS_KEY));
    if (colonIndex < 0) {
      return;
    }
    String questionsJson = text.substring(colonIndex + 1, questionsArrayEndIndex + 1).trim();

    try {
      List<GeminiQuestionEntry> questions =
          OBJECT_MAPPER.readValue(questionsJson, new TypeReference<>() {});
      questionsEmitted = true;
      onQuestionsReady.accept(questions);
    } catch (JsonProcessingException e) {
      log.error("questions 배열 파싱 실패: {}", e.getMessage());
    }
  }

  /** 스트림 에러 시 부분적으로 수신된 questions 파싱을 시도한다. */
  private void tryParsePartialQuestions() {
    String text = buffer.toString();
    int colonIndex = text.indexOf(':', text.indexOf(QUESTIONS_KEY));
    if (colonIndex < 0) {
      onError.accept(new IllegalStateException("questions 배열을 찾을 수 없습니다"));
      return;
    }
    String partial = text.substring(colonIndex + 1, questionsArrayEndIndex + 1).trim();

    try {
      List<GeminiQuestionEntry> questions =
          OBJECT_MAPPER.readValue(partial, new TypeReference<>() {});
      questionsEmitted = true;
      onQuestionsReady.accept(questions);
      onError.accept(new PartialResponseException("스트리밍 중단 — 문제는 부분 파싱됨", null));
    } catch (JsonProcessingException e) {
      onError.accept(new IllegalStateException("questions 부분 파싱도 실패", e));
    }
  }

  /** questions는 성공했으나 explanations가 실패한 경우를 나타내는 예외. */
  public static class PartialResponseException extends RuntimeException {

    public PartialResponseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
