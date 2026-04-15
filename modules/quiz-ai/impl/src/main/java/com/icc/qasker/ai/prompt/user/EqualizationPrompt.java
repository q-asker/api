package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EqualizationPrompt {

  /**
   * 선택지 균등화 프롬프트를 생성한다. preserveIndex 번째는 그대로 출력, 나머지만 길이를 맞춘다.
   *
   * @param selectionContents 전체 선택지 텍스트 목록 (4개)
   * @param preserveIndex 그대로 출력할 선택지 인덱스 (0-based)
   * @param targetLength 목표 글자수
   */
  public static String generate(
      List<String> selectionContents, int preserveIndex, int targetLength, String language) {
    int preserveNumber = preserveIndex + 1;

    String instruction =
        "EN".equals(language)
            ? """
            Equalize the length of the following %d statements to approximately %d characters.
            Output statement %d exactly as-is.
            For the other statements, preserve claims, conclusions, and tone. Only add modifiers.
            Preserve all markdown structure (```mermaid, ```code, tables, lists) exactly as-is.
            You MUST output in English."""
                .formatted(selectionContents.size(), targetLength + 5, preserveNumber)
            : """
            다음 %d개 서술문의 길이를 %d자 근처로 균등하게 맞추세요.
            %d번 서술문은 원문 그대로 출력하세요.
            나머지 서술문은 주장·결론·종결 어투를 원문 그대로 유지하고, 수식어만 추가하세요.
            마크다운 서식(```mermaid, ```코드, 테이블, 목록)은 구조 그대로 유지하세요."""
                .formatted(selectionContents.size(), targetLength + 5, preserveNumber);

    return """
        %s

        %s
        """
        .formatted(
            instruction,
            IntStream.range(0, selectionContents.size())
                .mapToObj(i -> (i + 1) + ". " + selectionContents.get(i))
                .collect(Collectors.joining("\n")));
  }
}
