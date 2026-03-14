package com.icc.qasker.quiz.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.entity.Explanation;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ReferencedPage;
import com.icc.qasker.quiz.entity.Selection;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProblemMapper {

  private final SelectionMapper selectionMapper;

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
                .map(selDto -> selectionMapper.fromResponse(selDto, problem))
                .collect(toList());

    Explanation explanation = Explanation.of(quizDto.getExplanation(), problem);

    List<ReferencedPage> referencedPages =
        quizDto.getReferencedPages() == null
            ? new ArrayList<>()
            : quizDto.getReferencedPages().stream()
                .map(page -> ReferencedPage.of(page, problem))
                .collect(toList());

    problem.bindChildren(selections, explanation, referencedPages);
    return problem;
  }
}
