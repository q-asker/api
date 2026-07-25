package com.icc.qasker.quizset.mapper;

import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.grading.AcceptedAnswerSanitizer;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전송 DTO → Problem(순수 서빙) 매핑. 품질/생성 근거는 problem_quality_log로 분리되어 여기서 다루지 않는다. */
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

    List<Selection> selections = toSelections(quizDto.getSelections(), problemSet.getQuizType());

    List<Integer> referencedPages =
        quizDto.getReferencedPages() == null ? new ArrayList<>() : quizDto.getReferencedPages();

    problem.bindQuizData(selections, referencedPages);
    problem.updateExplanation(quizDto.getExplanation());
    problem.updateAppliedInstruction(quizDto.getAppliedInstruction());
    return problem;
  }

  /**
   * AI 선지를 엔티티 Selection으로 변환한다. REAL_BLANK면 정답 선지에 인정 답 목록을 sanitize(FR-002a 함정 겹침 제거)해 저장하고, 그
   * 외에는 acceptedAnswers를 null로 둔다.
   */
  private List<Selection> toSelections(List<SelectionsOfAI> aiSelections, QuizType quizType) {
    if (aiSelections == null) {
      return new ArrayList<>();
    }
    boolean realBlank = quizType == QuizType.REAL_BLANK;
    List<List<String>> sanitizedAccepted =
        realBlank ? sanitizeAcceptedForCorrect(aiSelections) : null;

    List<Selection> result = new ArrayList<>(aiSelections.size());
    for (SelectionsOfAI s : aiSelections) {
      List<List<String>> accepted = (realBlank && s.isCorrect()) ? sanitizedAccepted : null;
      result.add(new Selection(s.getContent(), s.getExplanation(), s.isCorrect(), accepted));
    }
    return result;
  }

  private List<List<String>> sanitizeAcceptedForCorrect(List<SelectionsOfAI> aiSelections) {
    SelectionsOfAI correct =
        aiSelections.stream().filter(SelectionsOfAI::isCorrect).findFirst().orElse(null);
    if (correct == null) {
      return null;
    }
    List<String> trapContents =
        aiSelections.stream().filter(s -> !s.isCorrect()).map(SelectionsOfAI::getContent).toList();
    return AcceptedAnswerSanitizer.sanitize(
        correct.getContent(), trapContents, correct.getAcceptedAnswers());
  }
}
