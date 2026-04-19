package com.icc.qasker.quizset.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
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

    List<Selection> selections =
        quizDto.getSelections() == null
            ? new ArrayList<>()
            : quizDto.getSelections().stream()
                .map(s -> new Selection(s.getContent(), s.isCorrect()))
                .collect(toList());

    List<Integer> referencedPages =
        quizDto.getReferencedPages() == null ? new ArrayList<>() : quizDto.getReferencedPages();

    problem.bindQuizData(selections, referencedPages);
    problem.updateExplanation(quizDto.getExplanation());
    return problem;
  }
}
