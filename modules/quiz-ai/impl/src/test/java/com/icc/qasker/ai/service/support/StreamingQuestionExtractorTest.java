package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.structure.GeminiQuestion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 스트리밍 JSON 배열 추출기의 상태머신 회귀 테스트 (문서 검증 테스트 4).
 *
 * <p>제안 2(제네릭 통합) 전후로 파싱 동작이 동일함을 보장한다.
 */
class StreamingQuestionExtractorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private List<GeminiQuestion> collect(String... chunks) {
    List<GeminiQuestion> out = new ArrayList<>();
    StreamingJsonArrayExtractor<GeminiQuestion> extractor =
        new StreamingJsonArrayExtractor<>(objectMapper, GeminiQuestion.class, out::add, "MULTIPLE");
    for (String c : chunks) {
      extractor.feed(c);
    }
    return out;
  }

  @Test
  void emitsQuestionsInOrder() {
    String json =
        "{\"questions\":[{\"content\":\"Q1\",\"bloomsLevel\":\"Remember\",\"referencedPages\":[1],"
            + "\"selections\":[{\"content\":\"a\",\"correct\":true,\"explanation\":\"e\"}]},"
            + "{\"content\":\"Q2\",\"bloomsLevel\":\"Apply\",\"referencedPages\":[2],"
            + "\"selections\":[{\"content\":\"b\",\"correct\":false,\"explanation\":\"e\"}]}]}";

    List<GeminiQuestion> result = collect(json);

    assertThat(result).extracting(GeminiQuestion::content).containsExactly("Q1", "Q2");
  }

  @Test
  void ignoresBracesAndEscapesInsideStrings() {
    // content 문자열 내부에 중괄호와 이스케이프된 따옴표/백슬래시가 포함
    String json =
        "{\"questions\":[{\"content\":\"he said \\\"hi\\\" {x} \\\\ end\","
            + "\"bloomsLevel\":\"Remember\",\"referencedPages\":[1],"
            + "\"selections\":[{\"content\":\"a\",\"correct\":true,\"explanation\":\"e\"}]}]}";

    List<GeminiQuestion> result = collect(json);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).content()).isEqualTo("he said \"hi\" {x} \\ end");
  }

  @Test
  void handlesChunkBoundarySplittingObjectMidway() {
    String full =
        "{\"questions\":[{\"content\":\"Q1\",\"bloomsLevel\":\"Remember\",\"referencedPages\":[1],"
            + "\"selections\":[{\"content\":\"a\",\"correct\":true,\"explanation\":\"e\"}]}]}";
    int mid = full.length() / 2;

    List<GeminiQuestion> result = collect(full.substring(0, mid), full.substring(mid));

    assertThat(result).extracting(GeminiQuestion::content).containsExactly("Q1");
  }

  @Test
  void ignoresTextOutsideArray() {
    // 배열 밖 텍스트/공백은 무시되고 배열 내부 객체만 방출
    String json =
        "prefix noise {\"questions\": [ {\"content\":\"Q1\",\"bloomsLevel\":\"Remember\","
            + "\"referencedPages\":[1],\"selections\":[]} ] } trailing";

    List<GeminiQuestion> result = collect(json);

    assertThat(result).extracting(GeminiQuestion::content).containsExactly("Q1");
  }
}
