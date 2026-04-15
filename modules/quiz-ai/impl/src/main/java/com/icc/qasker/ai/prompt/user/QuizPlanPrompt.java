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

        위 문항별 참조 페이지에 나열된 **모든 문항**에 대해 2개 필드를 작성하세요.

        **format** — 사용할 마크다운 서식 1개:
        - none: 자료 없이 서술문만으로 충분한 경우
        - table: 2개 이상 항목의 속성 비교·대조
        - quote_list: 원문 인용 + 특징/조건 나열
        - mermaid: 절차, 흐름, 인과 관계 다이어그램
        - ordered_list: 단계, 우선순위, 랭킹
        - code_block: 강의노트에 소스 코드가 포함된 경우

        **formatUsage** — 선택한 서식의 활용 방안. 아래 형태 중 하나:
        - 질문문 활용: "질문문에서 [서식]을 ~에 배치하고 ~라는 질문을 한다"
        - 선택지 활용: "선택지에서 [서식]을 각 선택지에 배치하고 정오답을 가려내기 위한 내용 구성으로 활용한다"
        - 서식 없음: "서식 없이 서술문으로 구성한다\""""
        .formatted(chunkDescription);
  }

  private static String generateEN(String chunkDescription) {
    return """
        Analyze the lecture notes and choose the most appropriate markdown format for each question.

        [Question-to-page mapping]
        %s

        Write all 2 fields for **every question** listed above.

        **format** — one markdown format:
        - none: plain text is sufficient
        - table: comparing attributes of 2+ items
        - quote_list: original text citation + listing features/conditions
        - mermaid: procedures, flows, cause-effect diagrams
        - ordered_list: steps, priorities, rankings
        - code_block: when the lecture notes contain source code

        **formatUsage** — how to use the chosen format. One of:
        - In question: "Place [format] in the question statement for ~ and ask about ~"
        - In selections: "Place [format] in each selection to distinguish correct from incorrect answers"
        - No format: "Compose as plain statements\""""
        .formatted(chunkDescription);
  }
}
