package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.structure.GeminiProblem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiProblemSetMapper {

    /**
     * GeminiProblemSet → AIProblemSet 변환 + 선택지 셔플 + 번호 재할당.
     *
     * @param source        Gemini 응답 역직렬화 결과
     * @param strategyValue 퀴즈 타입 문자열 (MULTIPLE, BLANK, OX)
     * @param numberCounter 스레드 안전 번호 카운터
     * @return 변환된 AIProblemSet
     */
    public static AIProblemSet toDto(
        List<GeminiProblem> source,
        String strategyValue,
        List<Integer> referencedPages,
        AtomicInteger numberCounter
    ) {
        QuizType quizType = QuizType.valueOf(strategyValue);
        boolean shouldShuffle = quizType == QuizType.MULTIPLE || quizType == QuizType.BLANK;

        List<AIProblem> result = new ArrayList<>(source.size());

        for (GeminiProblem problem : source) {
            List<AISelection> selections = mapSelections(problem);

            if (shouldShuffle && !selections.isEmpty()) {
                selections = new ArrayList<>(selections);
                Collections.shuffle(selections);
            }

            int number = numberCounter.getAndIncrement();
            result.add(new AIProblem(number, problem.title(), selections, problem.explanation(),
                referencedPages));
        }

        return new AIProblemSet(result);
    }

    private static List<AISelection> mapSelections(GeminiProblem problem) {
        if (problem.selections() == null) {
            return List.of();
        }
        return problem.selections().stream()
            .map(gs -> new AISelection(gs.content(), gs.correct()))
            .toList();
    }
}
