package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EqualizationPrompt {

  /**
   * 오답 선택지를 균등화하는 프롬프트를 생성한다. 목표 길이를 명시하여 길이와 어투를 통일한다.
   *
   * @param selectionContents 오답 선택지 텍스트 목록
   * @param targetLength 목표 글자수 (정답 선택지의 길이)
   */
  public static String generate(List<String> selectionContents, int targetLength, String language) {

    String instruction =
        "EN".equals(language)
            ? """
            Equalize the length of the following %d statements to approximately %d characters, and unify their tone.
            Preserve the claims and conclusions of each statement as-is. Only add modifiers or supplementary explanations.
            Even if a statement contains factually incorrect content, preserve the original as-is.
            Preserve all markdown structure (```mermaid, ```code, tables, lists) exactly as-is.
            Your role is to equalize the length while preserving the content.
            You MUST output in English."""
                .formatted(selectionContents.size(), targetLength + 5)
            : """
            다음 %d개 서술문의 길이를 %d자 근처로 균등하게 맞추고, 어투를 통일하세요.
            각 서술문의 주장과 결론은 원문 그대로 유지하세요. 수식어나 부연 설명만 추가하세요.
            사실과 다른 내용이 포함되어 있어도 원문 그대로 유지하세요.
            마크다운 서식(```mermaid, ```코드, 테이블, 목록)은 구조 그대로 유지하세요.
            내용을 유지한 채 길이를 맞추는 것이 당신의 역할입니다."""
                .formatted(selectionContents.size(), targetLength + 5);

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
