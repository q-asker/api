package com.icc.qasker.quizset.mapper;

import static java.util.stream.Collectors.toList;

import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.entity.AcceptedAnswer;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
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

    List<Selection> selections =
        quizDto.getSelections() == null
            ? new ArrayList<>()
            : quizDto.getSelections().stream()
                .map(s -> new Selection(s.getContent(), s.getExplanation(), s.isCorrect()))
                .collect(toList());

    List<Integer> referencedPages =
        quizDto.getReferencedPages() == null ? new ArrayList<>() : quizDto.getReferencedPages();

    problem.bindQuizData(selections, referencedPages);
    problem.bindAcceptedAnswers(buildAcceptedAnswers(selections, quizDto.getAcceptedAnswers()));
    problem.updateExplanation(quizDto.getExplanation());
    problem.updateAppliedInstruction(quizDto.getAppliedInstruction());
    return problem;
  }

  /**
   * 정답 선지 content(다중 빈칸이면 콤마 구분)의 각 토큰을 모범답안(answer)으로 삼고, 같은 순서의 허용변형(accepted)과 짝지어 문항 레벨 허용답안을
   * 조립한다. 정답 선지가 없거나 정답 토큰이 비면 null(폴백 채점 대상). accepted는 빈칸 순서대로 매칭하고, 없는 빈칸은 빈 목록으로 채워 정렬(빈칸 개수)을
   * 정답 토큰 기준으로 보장한다.
   */
  static List<AcceptedAnswer> buildAcceptedAnswers(
      List<Selection> selections, List<List<String>> acceptedVariants) {
    String correctContent =
        selections.stream()
            .filter(Selection::correct)
            .map(Selection::content)
            .filter(c -> c != null && !c.isBlank())
            .findFirst()
            .orElse(null);
    if (correctContent == null) {
      return null;
    }

    List<AcceptedAnswer> result = new ArrayList<>();
    String[] answerTokens = correctContent.split(",");
    for (int i = 0; i < answerTokens.length; i++) {
      String answer = answerTokens[i].trim();
      if (answer.isEmpty()) {
        continue;
      }
      List<String> accepted =
          acceptedVariants != null && i < acceptedVariants.size() && acceptedVariants.get(i) != null
              ? List.copyOf(acceptedVariants.get(i))
              : List.of();
      result.add(new AcceptedAnswer(answer, accepted));
    }
    return result.isEmpty() ? null : result;
  }
}
