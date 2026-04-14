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
    int questionOffset = 1;
    for (int i = 0; i < chunks.size(); i++) {
      ChunkInfo chunk = chunks.get(i);
      int from = questionOffset;
      int to = questionOffset + chunk.quizCount() - 1;
      sb.append(
          "- %d~%d번 문항: %s 페이지 참조 (%d문제)"
              .formatted(from, to, chunk.referencedPages(), chunk.quizCount()));
      if (i < chunks.size() - 1) sb.append("\n");
      questionOffset += chunk.quizCount();
    }
    return sb.toString();
  }

  private static String generateKO(String chunkDescription) {
    return """
        강의노트를 분석하여 각 문항에 가장 적합한 마크다운 서식을 선택하세요.

        [문항별 참조 페이지]
        %s

        각 문항의 **format**을 결정하세요. 해당 문항이 참조하는 페이지의 내용을 가장 잘 표현할 수 있는 서식을 선택하세요.
        - table: 속성 비교, 2개 이상 항목의 특성 대조
        - quote_list: 원문 인용 + 특징/조건 나열
        - mermaid: 절차, 흐름, 인과 관계 다이어그램
        - ordered_list: 단계, 우선순위, 랭킹
        - code_block: 강의노트에 소스 코드가 포함된 경우에만 사용"""
        .formatted(chunkDescription);
  }

  private static String generateEN(String chunkDescription) {
    return """
        Analyze the lecture notes and choose the most appropriate markdown format for each question.

        [Question-to-page mapping]
        %s

        Decide the **format** for each question. Choose the format that best represents the content of the referenced pages.
        - table: attribute comparison, contrasting characteristics of 2+ items
        - quote_list: original text citation + listing features/conditions
        - mermaid: procedures, flows, cause-effect diagrams
        - ordered_list: steps, priorities, rankings
        - code_block: only when the lecture notes contain source code"""
        .formatted(chunkDescription);
  }
}
