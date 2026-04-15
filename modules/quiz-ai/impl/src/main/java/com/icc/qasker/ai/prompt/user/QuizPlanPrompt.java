package com.icc.qasker.ai.prompt.user;

import com.icc.qasker.ai.dto.ChunkInfo;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QuizPlanPrompt {

  /**
   * 문항 계획 프롬프트를 생성한다. 청크별 페이지 범위와 문제 수를 명시하여, LLM이 각 청크의 페이지 내용에 맞는 서식을 선택하게 한다.
   *
   * @param chunks 청크 목록 (참조 페이지 + 문제 수)
   * @param language 언어 (KO, EN)
   */
  public static String generate(List<ChunkInfo> chunks, String language) {
    String chunkDescription = buildChunkDescription(chunks);
    return "EN".equals(language) ? generateEN(chunkDescription) : generateKO(chunkDescription);
  }

  private static String buildChunkDescription(List<ChunkInfo> chunks) {
    StringBuilder sb = new StringBuilder();
    int questionNumber = 1;
    for (ChunkInfo chunk : chunks) {
      for (int q = 0; q < chunk.quizCount(); q++) {
        if (questionNumber > 1) sb.append("\n");
        sb.append("- %d번 문항: %s 페이지 참조".formatted(questionNumber, chunk.referencedPages()));
        questionNumber++;
      }
    }
    return sb.toString();
  }

  private static String generateKO(String chunkDescription) {
    return """
        강의노트를 분석하여 각 문항에 가장 적합한 마크다운 서식을 선택하세요.

        [문항별 참조 페이지]
        %s

        위 문항별 참조 페이지에 나열된 **모든 문항**에 대해 3개 필드를 작성하세요.

        **format** — 사용할 마크다운 서식 1개:
        - none: 자료 없이 서술문만으로 충분한 경우
        - table: 2개 이상 항목의 속성 비교·대조
        - quote_list: 원문 인용 + 특징/조건 나열
        - mermaid: 절차, 흐름, 인과 관계 다이어그램
        - ordered_list: 단계, 우선순위, 랭킹
        - code_block: 강의노트에 소스 코드가 포함된 경우

        **contentHint** — format이 none이 아니면 서식 활용법을 반드시 포함한 요청문:
        - table → "A와 B의 차이를 **표**로 비교하는 내용으로 문제 본문을 만들어주세요"
        - mermaid → "~과정을 **순서도**로 시각화하는 내용으로 문제 본문을 만들어주세요"
        - ordered_list → "~단계를 **번호 목록**으로 나열하는 내용으로 문제 본문을 만들어주세요"
        - quote_list → "~정의를 **인용**하고 특징을 나열하는 내용으로 문제 본문을 만들어주세요"
        - code_block → "~코드를 **코드 블록**으로 제시하는 내용으로 문제 본문을 만들어주세요"
        - none → "~하는 내용으로 문제 본문을 만들어주세요"
        **selectionHint** — "~하는 내용으로 선택지를 만들어주세요" 형태의 요청문."""
        .formatted(chunkDescription);
  }

  private static String generateEN(String chunkDescription) {
    return """
        Analyze the lecture notes and choose the most appropriate markdown format for each question.

        [Question-to-page mapping]
        %s

        Write all 3 fields for **every question** listed above.

        **format** — one markdown format:
        - none: plain text is sufficient
        - table: comparing attributes of 2+ items
        - quote_list: original text citation + listing features/conditions
        - mermaid: procedures, flows, cause-effect diagrams
        - ordered_list: steps, priorities, rankings
        - code_block: when the lecture notes contain source code

        **contentHint** — if format is not none, MUST include how to use the format:
        - table → "Please make the question body **comparing** A and B **in a table**"
        - mermaid → "Please make the question body **visualizing** the process **as a flowchart**"
        - ordered_list → "Please make the question body **listing** steps **in a numbered list**"
        - quote_list → "Please make the question body **quoting** the definition and **listing** features"
        - code_block → "Please make the question body **presenting** the code **in a code block**"
        - none → "Please make the question body about ~"
        **selectionHint** — a request like "Please make the selections about ~"."""
        .formatted(chunkDescription);
  }
}
