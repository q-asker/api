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

        각 문항의 **hint**를 한줄로 작성하세요.
        마크다운 서식(table, mermaid, quote, list, code 등) 활용 방안을 포함하세요. 서식이 필요없는 상황도 고려하세요.
        문항 줄기 구성법을 포함하세요
        선택지 구성법을 포함하세요
       """
        .formatted(chunkDescription);
  }

  private static String generateEN(String chunkDescription) {
    return """
        Analyze the lecture notes and choose the most appropriate markdown format for each question.

        [Question-to-page mapping]
        %s

        Write a **hint** for each question in one line. Include markdown format (table, mermaid, quote, list, code, etc.) and usage guidance.
        Form: "The question stem uses [format] for ~, and selections describe ~"
        If no resource is needed, write the hint without specifying a format."""
        .formatted(chunkDescription);
  }
}
