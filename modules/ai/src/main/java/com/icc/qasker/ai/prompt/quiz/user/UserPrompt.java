package com.icc.qasker.ai.prompt.quiz.user;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPrompt {

    public static String generate(List<Integer> referencedPages, int quizCount) {
        return """
            [생성 지시]
            - 첨부된 강의노트의 %s 페이지를 참고하여 정확히 %d개의 문제를 생성하세요.
            """.formatted(referencedPages, quizCount);
    }
}
