package com.icc.qasker.quiz.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.SelectionData;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProblemMapper {

  public Problem fromResponse(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
    Problem problem =
        Problem.builder()
            .id(ProblemId.builder().number(quizDto.getNumber()).build())
            .title(quizDto.getTitle())
            .problemSet(problemSet)
            .build();

    List<SelectionData> selections =
        quizDto.getSelections() == null
            ? new ArrayList<>()
            : quizDto.getSelections().stream()
                .map(s -> new SelectionData(s.getContent(), s.isCorrect()))
                .collect(toList());

    List<Integer> referencedPages =
        quizDto.getReferencedPages() == null ? new ArrayList<>() : quizDto.getReferencedPages();

    problem.bindChildren(selections, quizDto.getExplanation(), referencedPages);
    return problem;
  }
}
