package com.icc.qasker.ai.prompt.user;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EqualizationPrompt {

  public static String generate(List<String> selectionContents) {
    String numbered =
        IntStream.range(0, selectionContents.size())
            .mapToObj(i -> (i + 1) + ". " + selectionContents.get(i))
            .collect(Collectors.joining("\n"));

    return """
        다음 4개 서술문의 길이를 균등하게 맞추세요.
        가장 긴 서술문은 그대로 유지하고, 짧은 서술문을 가장 긴 것과 비슷한 길이로 늘려 작성하세요.
        각 서술문의 주장과 결론을 변경하지 마세요. 수식어나 부연 설명만 추가하세요.
        서술문에 사실과 다른 내용이 있더라도 원문 그대로 유지하세요. 내용을 교정하는 것은 당신의 역할이 아닙니다.

        %s
        """
        .formatted(numbered);
  }
}
