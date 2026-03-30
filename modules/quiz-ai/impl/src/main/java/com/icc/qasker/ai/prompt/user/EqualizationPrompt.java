package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EqualizationPrompt {

  /**
   * 모든 선택지를 포함하여 균등화 프롬프트를 생성한다. 각 서술문에 목표 길이를 명시하여 길이와 어투를 통일한다.
   *
   * @param selectionContents 전체 선택지 텍스트 목록 (4개)
   * @param targetLength 목표 글자수 (최장 서술문의 길이)
   * @param language 출력 언어 ("KO" 또는 "EN")
   */
  public static String generate(List<String> selectionContents, int targetLength, String language) {
    String unit = "EN".equals(language) ? " words" : "자";

    return """
        다음 4개 서술문의 길이를 %d%s 근처로 균등하게 맞추고, 어투를 통일하세요.
        각 서술문의 주장과 결론을 변경하지 마세요. 수식어나 부연 설명만 추가하세요.
        서술문에 사실과 다른 내용이 있더라도 원문 그대로 유지하세요.
        내용을 유지한 채 선택지 간 길이를 맞추는 것이 당신의 역할입니다.

        %s
        """
        .formatted(
            targetLength,
            unit,
            IntStream.range(0, selectionContents.size())
                .mapToObj(i -> (i + 1) + ". " + selectionContents.get(i))
                .collect(Collectors.joining("\n")));
  }
}
